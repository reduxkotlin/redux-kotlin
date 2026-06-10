---
tier: T1
concern: state-persistence
derives_from:
  - redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/StateSaver.kt → StateSaver
  - redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/RememberSaveableState.kt → rememberSaveableState
  - redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt → createModelStore
  - redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/StoreFactory.kt → createConcurrentModelStore
  - redux-kotlin-multimodel/src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt → withAll
  - redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/NotificationContext.kt → coalescingNotificationContext
api_files:
  - redux-kotlin-compose-saveable/api/redux-kotlin-compose-saveable.klib.api
  - redux-kotlin-routing/api/redux-kotlin-routing.klib.api
  - redux-kotlin-bundle/api/redux-kotlin-bundle.klib.api
  - redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api
  - redux-kotlin-concurrent/api/redux-kotlin-concurrent.klib.api
rules: [C, E]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 75388a1, date: 2026-06-10 }
---

# State persistence & restore (process death, rotation, relaunch)

> How to save store state and get it back with **no stale first frame**: `preloadedState` for state
> you persist yourself, `redux-kotlin-compose-saveable` for state the OS saves, and what must never
> be persisted at all.

## The split — two mechanisms, by storage owner

| State | Owner | Mechanism |
|---|---|---|
| Durable domain data (boards, session, settings) | You (db / files) | Load before store creation → `preloadedState` |
| Volatile UI state (route, filter, selected tab) | The OS (saved-instance state) | `StateSaver` + `rememberSaveableState` |
| Transient interaction state (edit mode, open overlay) | Nobody | Don't persist — restore neutral |
| Text drafts local to one composable | Compose | Plain `rememberSaveable` (no store involvement, Rule C) |

The invariant both library mechanisms enforce: **restore happens before the first read**, so the
first frame renders rehydrated state. Never restore by dispatching after the UI is up — that paints
the initial state first, then visibly jumps.

## Rehydrate at construction — `preloadedState`

For state you persist yourself, seed the store when you build it:

- Core: `createStore(reducer, restoredState)`.
- Routed `ModelState` stores:
  `redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt → createModelStore`
  and the bundle's
  `redux-kotlin-bundle/src/commonMain/kotlin/org/reduxkotlin/bundle/StoreFactory.kt → createConcurrentModelStore`
  take `preloadedState: ModelState?`. The restored models are overlaid onto the declared defaults via
  `redux-kotlin-multimodel/src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt → withAll`
  — the preloaded key set must be a **subset** of the declared slots; slots you don't preload keep
  their declared initial value.

```kotlin
createConcurrentModelStore(
    preloadedState = ModelState.of(NavModel(restoredStack), FilterModel(restoredQuery)),
) { /* model(...) declarations — every preloaded class must be declared */ }
```

Consistency background (sync writes, async notify) → [store-consistency-model.md](./store-consistency-model.md).

## OS-saved snapshots — `redux-kotlin-compose-saveable`

Two public symbols
(`redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/StateSaver.kt → StateSaver`,
`redux-kotlin-compose-saveable/src/commonMain/kotlin/org/reduxkotlin/compose/saveable/RememberSaveableState.kt → rememberSaveableState`):

- `StateSaver(serializer, save, restore, json)` — a `@Serializable` snapshot of *just the fields
  worth keeping*, a `save` projection from state, and a `restore` that turns a decoded snapshot
  into an **action** the reducer applies. Stateless and store-free: build one instance and reuse it
  across scopes; the serialization round-trip is unit-testable without Compose.
- `store.rememberSaveableState(saver, key?)` — the anchor. Place it **once per persisted scope**,
  *above* every composable that reads the restored slice.

Behavioral contract (all KDoc'd in the sources above):

- **Synchronous restore.** On a real restore the snapshot is decoded and the restore action
  dispatched *during composition of the anchor*, before any child binding reads the store — no
  stale first frame on a synchronous-dispatch store.
- **Exactly once.** `SaveableStateRegistry.consumeRestored` yields the value once; recomposition
  cannot double-apply. Cold start dispatches nothing.
- **Lazy save.** The projection is serialized only when the platform actually saves; there is no
  per-dispatch cost and no steady-state subscription.
- **Best-effort decode.** An undecodable snapshot (schema change) is dropped → cold start, no crash.
  Use `Json { ignoreUnknownKeys = true }` for additive changes, a `version` field for breaking ones.
- **Main thread.** Restore reads/dispatches on main — the store must accept main-thread access
  (the concurrent/bundle store does).
- Desktop / JS / wasm have no OS saved-instance state: the anchor is a no-op there.

**Keys:** the default key is positional (call-site composite hash). Pass an explicit stable `key`
whenever scopes can collide — multiple anchors, anchors inside lists/nav graphs, or per-entity
scopes. Scope the key to the data's identity (e.g. `key = "account-ui-$accountId"`) so one
account's snapshot can never restore into another.

## What NOT to persist

- **Modes/overlays** — restore a detail screen in *view* mode even if the process died in *edit*
  mode; the snapshot DTO simply omits the field. Persisted modes make interrupted interactions
  sticky.
- **Drafts** — keep in-progress text out of the store: plain `rememberSaveable` rides the same
  registry with zero store coupling (Rule C).
- **Anything derivable** — recompute, don't snapshot.

## Notification timing (Rule E)

With a concurrent store, writes are synchronous but subscriber callbacks follow the
`NotificationContext`. Wrap the platform main-thread post with
`redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/NotificationContext.kt → coalescingNotificationContext`
(`isOnTargetThread` + `post`): main-thread dispatches (including the restore dispatch) notify
inline with no extra frame of latency, while off-main effect dispatches still marshal to main.
The Compose bindings themselves read `getState()` synchronously on every read, so they never
*render* stale state either way — see [store-consistency-model.md](./store-consistency-model.md).

## TaskFlow integration (lessons)

The taskflow sample wires all of this — saver + anchor in
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/persistence/UiSnapshot.kt → accountUiSaver, RestoreUiState`.
Its hard-won lessons, in checklist form:

1. One reused `StateSaver<ModelState, UiSnapshot>` for nav stack + filter; restore routed onto the
   slots via a single `RestoreUiState` action.
2. Account-scoped anchor key (`"account-ui-$accountId"`) — positional keys collided across accounts.
3. `CardDetail` restores in **View** mode; the DTO has no edit-mode field.
4. New-card draft via plain `rememberSaveable` in the screen, not the store.
5. First paint gated on app bootstrap (account directory loaded from db) so returning users never
   see a Login flash; on Android, also gated on window-insets dispatch to avoid a status-bar jump
   on process-death restore.
6. `coalescingNotificationContext` as the main `NotificationContext` so off-main sync/effects
   dispatches never leave bindings a frame behind.

## Verify loop

Library round-trip tests live in `redux-kotlin-compose-saveable` (`commonTest` codec/registry tests
+ `jvmTest` end-to-end restore): `./gradlew :redux-kotlin-compose-saveable:jvmTest`. Preload
overlay tests: `./gradlew :redux-kotlin-routing:jvmTest` and `./gradlew :redux-kotlin-bundle:jvmTest`.
Then `./gradlew detektAll`; `./gradlew apiCheck` if a library surface changed.

## See also

- [store-consistency-model.md](./store-consistency-model.md) — why preload-at-construction and
  synchronous binding reads make restore flash-free.
- [store-setup.md](./store-setup.md) — the store factories these parameters hang off.
- [compose-binding.md](./compose-binding.md) — Rule C; what belongs in the store vs the composable.
- [README](./README.md)
