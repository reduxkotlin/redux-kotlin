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
 * decoded and dispatched via [StateSaver.restore] exactly once, before
 * downstream bindings re-sample the store. On cold start nothing is
 * dispatched. While mounted, the latest projection is provided to the
 * registry and serialized only when the platform actually saves.
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
    val finalKey = key ?: currentCompositeKeyHash.toString(radix = 36) // KMP-safe radix

    // Consume during composition (what rememberSaveable does internally).
    // Defensive decode: schema drift across app versions → null → treated
    // as a cold start instead of a crash.
    val restored: Snapshot? = remember(store, finalKey) {
        (registry?.consumeRestored(finalKey) as? String)?.let { encoded ->
            runCatching { saver.json.decodeFromString(saver.serializer, encoded) }
                .getOrNull()
        }
    }

    DisposableEffect(store, registry, finalKey) {
        // Dispatch ONLY on a real restore. Cold start dispatches nothing.
        if (restored != null) {
            store.dispatch(saver.restore(restored))
        }
        // Provider is invoked by the platform at save time and samples
        // store.state THEN — no steady-state subscription, no per-dispatch
        // work, serialization only when actually saving.
        val entry = registry?.registerProvider(finalKey) {
            saver.json.encodeToString(saver.serializer, saver.save(store.state))
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
  deliberately; consider `Json { ignoreUnknownKeys = true }` for additive
  changes; treat restore as best-effort.
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
- **Multiple anchors** — each persists its own key; recommend a single
  root anchor for a whole-store snapshot.
- **Decode failure** — cold start (see §8).

## 11. Testing strategy

- **commonTest** — `StateSaver` round-trip: `encodeToString` →
  `decodeFromString` reproduces the snapshot; `restore(save(state))`
  produces the expected action. No Compose needed (the value type holds no
  composition state).
- **jvmTest (desktop `compose.uiTest`)** — `StateRestorationTester`
  emulates save → restore. Assert:
  1. after restoration the store was rehydrated via the dispatched action;
  2. a child `fieldState` shows the **restored** value, not the initial
     value — i.e. the clobber is actually solved;
  3. cold start dispatches **no** rehydrate action (spy/record dispatches).
  Runs on the JVM/desktop host — no Android device required.

## 12. Out of scope / future

- **Reducer-combinator ergonomics** — a `withRehydrate(reducer) { snap,
  state -> … }` that removes the user-written action by intercepting an
  internal rehydrate action. Nicer, but couples the snapshot type into
  store setup; deferred (YAGNI for v1).
- **Per-field saveable bindings** — `saveableField(prop, …)`; rejected for
  v1 (fragments ownership, N write-backs; same dispatch cost as the
  snapshot without the centralization).
- **Long-term disk persistence** — separate concern/module.
- **Zero-flash-on-restore variant** — documented, not shipped.
