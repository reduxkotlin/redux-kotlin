---
tier: T1
concern: store-consistency-model
derives_from:
  - redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/ConcurrentStore.kt → CallerSerializedStore
  - redux-kotlin-concurrent/src/commonMain/kotlin/org/reduxkotlin/concurrent/NotificationContext.kt → coalescingNotificationContext
  - redux-kotlin-routing/src/commonMain/kotlin/org/reduxkotlin/routing/CreateModelStore.kt → createModelStore
  - redux-kotlin-multimodel/src/commonMain/kotlin/org/reduxkotlin/multimodel/ModelState.kt → withAll
  - redux-kotlin-compose/src/commonMain/kotlin/org/reduxkotlin/compose/FieldState.kt → selectorState, fieldState
api_files:
  - redux-kotlin-concurrent/api/redux-kotlin-concurrent.klib.api
  - redux-kotlin-bundle/api/redux-kotlin-bundle.klib.api
  - redux-kotlin-routing/api/redux-kotlin-routing.klib.api
  - redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api
  - redux-kotlin-compose/api/redux-kotlin-compose.klib.api
rules: [E]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 75388a1, date: 2026-06-10 }
---

# Store consistency model (concurrent store + Compose)

The concurrent store (`createConcurrentStore` / `createConcurrentModelStore`, the bundle default)
separates two things:

- **State writes are synchronous.** `dispatch` runs the reducer under a writer lock and publishes the
  state mirror *before it returns*. Immediately after `dispatch`, `getState()` / `getModel<T>()` return
  the new state.
- **Subscriber notifications follow the `NotificationContext`.** With a posting context (e.g. a bare
  `Handler.post` on Android), callbacks run on a *later* loop iteration — eventual consistency.

## Don't branch on a binding right after dispatch

`selectorState` / `fieldState` are driven by the subscription. As of the lag-free rewrite they read
`getState()` synchronously on every read (the subscription only schedules recomposition), so a binding
value is always current at read time. But if you mix a *synchronously-flipped* flag with a value
derived from the store, read the value via `getState()` / `getModel()` — not a stale local copy.

## Rehydrate via preloadedState, not a post-paint dispatch

Seed restored state at construction so the first render is correct (no intermediate flash):

    createConcurrentModelStore(
        preloadedState = ModelState.of(NavModel(restoredStack), FilterModel(restoredQuery)),
    ) { /* model(...) declarations */ }

`preloadedState`'s key set must be a subset of the declared models (it overlays them via
`ModelState.withAll`).

## Main-thread notifications

Wrap your platform main-thread post with `coalescingNotificationContext`:

    coalescingNotificationContext(
        isOnTargetThread = { isOnMainThread() },
        post = { block -> handler.post(block) },
    )

A main-thread dispatch then notifies subscribers inline (no extra frame of latency), while off-main
dispatches still marshal to the main thread.
