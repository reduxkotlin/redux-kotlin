# redux-kotlin-compose-saveable — Design

- **Date:** 2026-06-09
- **Status:** Approved (brainstorming complete; pending spec review)
- **Module:** `redux-kotlin-compose-saveable` (new companion)

## 1. Goal

Let Redux state that backs a Compose UI **survive Android configuration
changes / rotation and process (app) death**, using a minimal serialized
snapshot of only the fields worth keeping. The mechanism rides Compose's
own saved-state machinery, so rotation and process death are covered by a
single code path, and degrades gracefully on non-Android targets.

Explicitly **out of scope**: long-term disk persistence (DataStore/file
across full restarts/updates). That is a different concern and can be a
separate module later.

## 2. Background & the core problem

The existing `redux-kotlin-compose` bindings (`fieldState`,
`selectorState`) are strictly one-directional: `store.state → Compose
State`. They re-sample `store.state` inside `DisposableEffect` and
overwrite the Compose value.

Target apps run the store as a **process/DI singleton**. Consequences:

- **Rotation** already preserves store state for free — the process lives
  and the singleton holds. No work required for the store's own state.
- **Process death** re-creates the singleton from its *initial* state, so
  that state is lost.

Therefore the real target is process-death restore. Compose's
`SaveableStateRegistry` (the primitive under `rememberSaveable`) covers
**both** rotation and process death through the same
`registry → savedInstanceState` path, so building on it gets rotation
included regardless.

### The clobber problem (the design's load-bearing constraint)

A Redux store can only change via `dispatch`. If a snapshot is restored
but *not written back into the store*, the store still holds its initial
state, and the one-directional bindings will **immediately overwrite the
restored value with the store's initial value** on first subscription.

So saveable support fundamentally requires a **rehydrate action**, not
just a `Saver`. The restored snapshot must be pushed back into the store
via `dispatch` so the store — the single source of truth — is correct
before any binding re-samples it.

## 3. Locked decisions

| Decision | Choice | Why |
|---|---|---|
| API shape | Store-anchored snapshot | One write-back keeps single-source-of-truth; minimal payload via projection |
| Module | New companion `redux-kotlin-compose-saveable` | Matches repo's opt-in-companion ethos; base compose module stays dependency-light |
| Serialization | kotlinx.serialization (String) | Fully KMP; Bundle-safe; already a repo dependency |
| Restore detection | Drive `SaveableStateRegistry` directly | Knows whether a restore actually happened → dispatch only then (no idempotency assumption) |
| Public unit | `StateSaver` value type | Clean call site; serialization round-trip testable without Compose |
| Restore write-back | User-supplied `restore: (Snapshot) -> Any` action | Zero-magic; works with any action/middleware; no store-setup coupling |

## 4. Module structure

`redux-kotlin-compose-saveable`, package `org.reduxkotlin.compose.saveable`,
mirroring the `redux-kotlin-registry` template.

- Plugins: `convention.library-mpp-loved`, compose-compiler,
  compose-multiplatform, `convention.publishing-mpp`.
- `commonMain` deps:
  - `api(project(":redux-kotlin-compose"))`
  - `implementation(compose.runtime)`
  - `implementation(compose.runtimeSaveable)` — provides
    `SaveableStateRegistry`, `LocalSaveableStateRegistry`,
    `currentCompositeKeyHash`.
  - `implementation(libs.kotlinx.serialization.json)`
  - Apply the `kotlinx-serialization` plugin (for user `@Serializable`
    snapshot types compiled in their own modules; the library itself uses
    the runtime API with explicit `KSerializer`).
- Same KMP targets as `redux-kotlin-compose`.
- Register in `settings.gradle.kts`.

## 5. Public API (2 symbols)

```kotlin
package org.reduxkotlin.compose.saveable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.reduxkotlin.Store

/**
 * Describes how a slice of store state [S] is projected to a small
 * serializable [Snapshot], and how a restored snapshot is turned back
 * into an action the store's reducer applies.
 *
 * Holds no Compose state — its [save]/[restore]/serialization round-trip
 * is unit-testable without a composition. Reuse one instance across
 * screens.
 */
public class StateSaver<S, Snapshot : Any>(
    /** Serializer for the [Snapshot] type (e.g. `MySnapshot.serializer()`). */
    public val serializer: KSerializer<Snapshot>,
    /** Project current state to the minimal snapshot worth persisting. */
    public val save: (S) -> Snapshot,
    /** Turn a restored snapshot into an action the reducer applies. */
    public val restore: (Snapshot) -> Any,
    /** JSON codec; override to tune (e.g. `ignoreUnknownKeys`). */
    public val json: Json = Json,
)

/**
 * Anchors saveable persistence for [this] store to the enclosing
 * [androidx.compose.runtime.saveable.SaveableStateRegistry]. Place it
 * **once** per persisted scope (typically near the root, or once per
 * screen).
 *
 * On a real restore (rotation or process death) the saved snapshot is
 * decoded and dispatched via [StateSaver.restore] exactly once, so
 * downstream bindings observe the rehydrated store (a single stale frame
 * is possible before they re-sample). On cold start nothing is dispatched.
 * While mounted, the latest projection is provided to the registry and
 * serialized only when the platform actually saves.
 *
 * Returns [Unit]; named to pair with [fieldState]/[selectorState].
 *
 * @param key stable registry key. Required when multiple anchors exist,
 *   inside lists, or across navigation where positional keys collide.
 *   Defaults to the call-site composite key.
 */
@Composable
public fun <S, Snapshot : Any> Store<S>.rememberSaveableState(
    saver: StateSaver<S, Snapshot>,
    key: String? = null,
)
```

### Usage

```kotlin
@Serializable
data class UiSnapshot(val tab: Int, val query: String)

val uiSaver = StateSaver(
    serializer = UiSnapshot.serializer(),
    save = { s: AppState -> UiSnapshot(s.tab, s.query) },
    restore = { RehydrateUi(it.tab, it.query) },   // reducer merges these onto state
)

@Composable
fun App(store: Store<AppState>) {
    store.rememberSaveableState(uiSaver)
    // … screen content; child fieldState bindings see the rehydrated store
}
```

The reducer handling `RehydrateUi` is user code — the library plumbs the
action; the user applies it (state transitions belong in reducers).

## 6. Mechanism

```kotlin
@Composable
public fun <S, Snapshot : Any> Store<S>.rememberSaveableState(
    saver: StateSaver<S, Snapshot>,
    key: String?,
) {
    val store = this
    val registry = LocalSaveableStateRegistry.current
    // Positional key derived from the composite-key-hash API the pinned
    // Compose version exposes (`currentCompositeKeyHash`; newer Compose may
    // name it `currentCompositeKeyHashCode`). KMP-safe radix.
    val finalKey = key ?: currentCompositeKeyHash.toString(radix = 36)

    // One effect does both halves; nothing here is used as a composition
    // value, so consuming at commit (rather than during composition) is
    // fine and removes the extra `remember`.
    DisposableEffect(store, registry, finalKey) {
        // Restore: consume the saved value (if any) and write it back into
        // the store via dispatch — ONLY on a real restore. Cold start
        // consumes null and dispatches nothing. Defensive decode: schema
        // drift across app versions → null → treated as a cold start
        // instead of a crash.
        val restored = (registry?.consumeRestored(finalKey) as? String)?.let { encoded ->
            runCatching { saver.json.decodeFromString(saver.serializer, encoded) }.getOrNull()
        }
        if (restored != null) {
            store.dispatch(saver.restore(restored))
        }
        // Save: provider is invoked by the platform at save time and samples
        // store.state THEN — no steady-state subscription, no per-dispatch
        // work, serialization only when actually saving. Defensive: a
        // throwing save()/encode returns null (skip this save cycle) so it
        // can never crash performSave.
        val entry = registry?.registerProvider(finalKey) {
            runCatching {
                saver.json.encodeToString(saver.serializer, saver.save(store.state))
            }.getOrNull()
        }
        onDispose { entry?.unregister() }
    }
}
```

Three load-bearing properties:

1. **Restore-only dispatch.** Because we consume the registry directly we
   *know* whether a restore occurred; we never rely on the reducer being
   idempotent under `restore∘save`, and cold start has zero side effects.
2. **Fixes the clobber.** The restored value is written back via
   `dispatch`, so child `fieldState`/`selectorState` re-sample the
   rehydrated store, not stale initial state.
3. **Zero steady-state cost.** No subscription, no `MutableState` holder;
   `save()` + `encode` run only at save time (≈ once per backgrounding).
   Both the restore decode and the save encode are wrapped in
   `runCatching` so neither a corrupt restore nor a failing save can crash
   the app.

**Ordering / one-frame flash.** The rehydrate dispatch runs at commit
(`DisposableEffect`). On restore there can be a single stale frame before
child bindings receive the rehydrate notification — identical in class to
the existing B3 race the compose module already documents and accepts. A
zero-flash variant (dispatch during composition, before children compose)
exists but trades idiom for timing; **not shipped** in v1 — documented
only.

## 7. Threading (precondition, not library code)

- `registerProvider`'s lambda runs on the **main thread** at save time and
  reads `store.state`. On the concurrent/threadsafe store (what the bundle
  ships) reads are safe. On a plain core store dispatched from another
  thread this is an unsynchronized ref read — the same pre-existing hazard
  as `fieldState`.
- `store.dispatch(...)` runs on **main** (inside `DisposableEffect`). A
  `SameThreadEnforced` store created off-main will **throw**.

**Contract:** the persisted store must accept main-thread reads and
dispatch — i.e. the Compose-facing store (concurrent/threadsafe, or
main-confined). This is documented in the KDoc and the module README; the
library adds no threading machinery of its own.

## 8. Serialization, versioning, errors

- Default codec encodes the snapshot to a `String` (Bundle-safe;
  cross-platform). `String` passes `SaveableStateRegistry.canBeSaved`.
- **Schema drift** (saved snapshot no longer matches the type): decode is
  wrapped in `runCatching → null`, so restore falls back to a clean cold
  start instead of crashing. Guidance: evolve the `@Serializable` snapshot
  deliberately; pass `Json { ignoreUnknownKeys = true }` (via
  `StateSaver.json`) for additive changes; for breaking changes include a
  `version` field in the snapshot and ignore/branch on mismatch in
  `restore`. Treat restore as best-effort — a dropped snapshot only means a
  cold start, never a crash.
- **Payload size:** Android `Bundle` has a ~500 KB `TransactionTooLarge`
  ceiling. The projection model structurally encourages "few fields"; the
  README states the limit.

## 9. Platform behavior

| Target | Rotation | Process death |
|---|---|---|
| Android | ✅ registry retained | ✅ → savedInstanceState |
| iOS (Compose MP) | ✅ | ✅ state restoration |
| Desktop / JS / wasm | ✅ in-process | n/a (no OS restore concept) — degrades gracefully |

One mechanism, both survival levels, no platform branching in our code. If
no `SaveableStateRegistry` is present (`LocalSaveableStateRegistry.current
== null`), the anchor is a no-op (no persistence) — safe default.

## 10. Edge cases

- **Null registry** — guarded with `?`; degrades to no persistence.
- **Key collisions** — `registerProvider` throws on duplicate keys within
  a registry; positional auto-key disambiguates single-anchor use; lists /
  multiple anchors / nav require an explicit `key`. Documented.
- **Auto-key stability** — the positional key is only stable across
  process death if the call-site composition structure is identical on
  restore (the same assumption `rememberSaveable` makes). For anchors
  under conditional/dynamic structure, pass an explicit `key`.
- **Multiple anchors** — each persists its own key; recommend a single
  root anchor for a whole-store snapshot.
- **Decode failure** — cold start (see §8).

## 11. Testing strategy

- **commonTest — `StateSaver` round-trip.** `encodeToString` →
  `decodeFromString` reproduces the snapshot; `restore(save(state))`
  produces the expected action; a corrupt/old encoded string decodes to
  `null`. No Compose needed (the value type holds no composition state).
- **commonTest — manual registry round-trip (primary mechanism test).**
  Drive the documented `SaveableStateRegistry` contract directly, no UI
  harness: construct a registry, register the provider, call
  `performSave()` to collect the encoded map, build a *second* registry
  seeded with that map (simulating process death), and run the anchor's
  restore path. Assert against a real `createStore`:
  1. restore dispatches the rehydrate action and the store holds the
     restored value;
  2. **cold start** (empty restored map) dispatches **nothing** (record
     dispatches via a spy middleware/subscriber);
  3. a throwing `save()` yields a `null` provider value (no crash).
  Deterministic and host-independent — this is where the correctness
  guarantees are pinned.
- **jvmTest (desktop `compose.uiTest`) — integration.**
  `StateRestorationTester` (available in `ui-test` `commonMain`) emulates
  save → restore through a real composition; assert a child `fieldState`
  shows the **restored** value, not the initial one — i.e. the clobber is
  solved end-to-end. Runs on the JVM/desktop host; no Android device.

## 12. Build & integration deliverables

Beyond the source + tests, shipping a new published module requires:

- **`settings.gradle.kts`** — `include(":redux-kotlin-compose-saveable")`.
- **Public-API dump** — run `./gradlew apiDump` and commit the generated
  `redux-kotlin-compose-saveable/api/*.api`; `apiCheck` (part of `build`)
  guards it thereafter.
- **`redux-kotlin-bom`** — add the new module as a constrained dependency
  so BOM consumers get an aligned version.
- **`redux-kotlin-bundle-compose` membership — DECIDED: include.**
  `bundle-compose` is multimodel-centric today (`redux-kotlin-bundle` +
  `redux-kotlin-compose-multimodel`); add
  `api(project(":redux-kotlin-compose-saveable"))` so saved-state ships as
  a baseline of the Compose bundle. (Saveable composes with any `Store<S>`
  incl. `ModelState`, so this is consistent.)
- **Docs** — module entry in `CLAUDE.md` / `docs/agent/_fragments/
  modules.md`, and a short usage page on the docs site.

### Note: works with `ModelState` / multimodel

The API is single-`Store<S>`, but `S` is unconstrained — including
`ModelState`. The projection `save: (S) -> Snapshot` reads whatever fields
matter (across models), and `restore` dispatches a single action the
reducer applies. No multimodel-specific variant is needed; the
`bundle-compose` (multimodel) audience is served by the same two symbols.

## 13. Out of scope / future

- **Reducer-combinator ergonomics** — a `withRehydrate(reducer) { snap,
  state -> … }` that removes the user-written action by intercepting an
  internal rehydrate action. Nicer, but couples the snapshot type into
  store setup; deferred (YAGNI for v1).
- **Per-field saveable bindings** — `saveableField(prop, …)`; rejected for
  v1 (fragments ownership, N write-backs; same dispatch cost as the
  snapshot without the centralization).
- **Long-term disk persistence** — separate concern/module.
- **Zero-flash-on-restore variant** — documented, not shipped.
