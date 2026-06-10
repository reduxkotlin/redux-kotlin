---
tier: T1
concern: store-setup
derives_from:
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AppStore.kt → createAppStore
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → createAccountStore, AccountStoreHandle, declareAccountModels
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountRegistry.kt → AccountRegistry
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/StoreExt.kt → getModel
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Notification.kt → mainNotificationContext
api_files:
  - redux-kotlin-bundle/api/redux-kotlin-bundle.klib.api
  - redux-kotlin-concurrent/api/redux-kotlin-concurrent.klib.api
  - redux-kotlin-registry/api/redux-kotlin-registry.klib.api
  - redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api
  - redux-kotlin-routing/api/redux-kotlin-routing.klib.api
rules: [E]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 3c1cd67, date: 2026-06-04 }
---

# Store setup & topology

> How taskflow builds its stores: when one store is enough, when to split into a root + per-account
> topology, how the routing DSL declares model slots, and how `NotificationContext` keeps subscriber
> callbacks on the UI thread.

## The store factory

Taskflow uses the concurrent multimodel store from the bundle — `createConcurrentModelStore` (the
`CallerSerialized` strategy: lock-free reads, reentrant-lock-serialized writes), holding a
`ModelState` heterogeneous model bag. Both factories build the same kind of store:

- Root: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AppStore.kt → createAppStore`
- Per account: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → createAccountStore`

A single `createConcurrentModelStore(...) { declare slots }` call with one slot is the floor; you do
not need any of the topology below for a one-screen app. The module that supplies it is the bundle —
see [modularization.md](./modularization.md).

Both `createModelStore` and `createConcurrentModelStore` take an optional `preloadedState:
ModelState?` that overlays restored/persisted models onto the declared defaults at construction
(key set must be a subset of the declared slots), so the first read already reflects rehydrated
state — use it instead of a post-paint dispatch. Full treatment →
[state-persistence.md](./state-persistence.md) and
[store-consistency-model.md](./store-consistency-model.md).

## One store vs. two

Taskflow runs **N+1 stores**: one root store plus one store per logged-in account.

- **Root store** (`createAppStore`) holds cross-account state only: the account directory, app
  settings (theme + the demo's fake-backend knobs), and the login flow. It carries **no middleware**.
- **Per-account store** (`createAccountStore`) holds one account's board state behind the
  `activityLogger → undo → effects` middleware pipeline, and owns that account's background
  `CoroutineScope`, sync repository, and bot.

**Decision rule — split when isolation has a lifetime.** Reach for a second store (or a registry of
them) when a sub-domain has (a) independent state that must not leak across instances, and (b) a
lifecycle you tear down as a unit. Each account here gets its own `CoroutineScope(SupervisorJob() +
Dispatchers.Default)`; removing an account cancels that scope and every effect/sync/bot coroutine
under it in one move. If your sub-domains share one lifetime and never need isolation, stay with one
store and more slots. Cross-store reads go one way: per-account effects read the root store's settings
via `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/StoreExt.kt → getModel`;
the root never reaches into an account store.

## The routing DSL — declaring slots

A `ModelState` store is built by declaring each slot up front and routing action classes to reducers.
The DSL is `model(InitialValue()) { on<ActionType> { state, action -> reducer(state, action) } }`.

- Root slots: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AppStore.kt → createAppStore`
  declares `AccountsModel`, `AppSettingsModel`, `AuthFlowModel`.
- Per-account slots: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → declareAccountModels`
  declares nine — `SessionModel`, `NavModel`, `BoardListModel`, `CollaboratorsModel`, `BoardModel`,
  `FilterModel`, `UndoModel`, `SyncModel`, `ActivityModel`.

Two routing facts that shape how you write reducers:

- **Declare every slot up front.** `ModelState.get` fails for an undeclared model, so the slot set is
  fixed at construction; you cannot add a slot at runtime.
- **Routing matches the exact leaf class** and returns the same model instance for unhandled actions,
  so a reducer's own `when` only handles the leaves it registered, ending in `else -> model`. The same
  shared action (e.g. `BoardClosed`) can be registered on several slots; each registered handler fires
  and untouched slots are left alone. Multi-slot reducers that need identity take a captured `selfId`
  from the wiring rather than reading it at runtime (Rule G — see [effects-sync.md](./effects-sync.md)).

## Threading — NotificationContext (Rule E)

Both factories default their notification context to
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/platform/Notification.kt → mainNotificationContext`,
a platform shim (see [platform-shims.md](./platform-shims.md)) that marshals subscriber callbacks to
the UI main thread. This is what lets effects dispatch from a background scope with **no explicit main
hop**: the write runs serialized, and the store notifies subscribers through the context, which posts
them to main. Tests override it with an inline context to dispatch synchronously on the caller thread.
For a lag-free variant, build the context with
`redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/NotificationContext.kt → coalescingNotificationContext`
— it runs the callback inline when the dispatch is already on the target (main) thread and posts
otherwise, so a main-thread dispatch never waits a loop iteration (see
[store-consistency-model.md](./store-consistency-model.md)).

## Per-account lifecycle — the registry

`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountRegistry.kt → AccountRegistry`
owns the per-account stores. It keeps two parallel structures: a `StoreRegistry<AccountId, ModelState>`
(from `redux-kotlin-registry`) for lock-free store lookup, and a map of
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → AccountStoreHandle`
for the disposable resources (scope, sync repo, bot job). `getOrCreate` builds-or-returns; `remove`
cancels the scope and forgets both entries. Use a registry whenever the number of stores is dynamic and
keyed; a fixed pair of stores does not need one.

## Verify loop

`./gradlew :examples:taskflow:composeApp:jvmTest` (compiles the jvm target and runs
`commonTest` + `jvmTest`, including the store-topology tests `AppStoreTest` and `AccountRegistryTest`),
then `./gradlew detektAll`, then `./gradlew apiCheck` if you touched a library module's public API
(taskflow itself has none; the backing surfaces are the `api_files` above). Full treatment →
[testing.md](./testing.md).

## See also

- [feature-slice.md](./feature-slice.md) — what a slot/reducer/screen looks like inside one feature.
- [effects-sync.md](./effects-sync.md) — the middleware pipeline this wires.
- [modularization.md](./modularization.md) — which module supplies each factory.
- [README](./README.md)
