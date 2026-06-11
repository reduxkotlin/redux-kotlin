---
tier: T1
concern: effects-sync
derives_from:
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/EffectsMiddleware.kt → effectsMiddleware
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/sync/SyncEngine.kt → SyncEngine, SyncStatus
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/sync/SyncRepository.kt → SyncRepository
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/remote/RemoteApi.kt → RemoteApi
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/remote/PushResult.kt → PushResult
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/local/LocalStore.kt → LocalStore
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/CardActions.kt → InverseOp, CardOpFailed, CardOpSucceeded
  - examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/infra/data/OfflineSyncE2ETest.kt → OfflineSyncE2ETest
api_files:
  - redux-kotlin-bundle/api/redux-kotlin-bundle.klib.api
rules: [E, F, G, I]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: ab2fb5b, date: 2026-06-11 }
---

# Effects + sync (Rule E)

> Where intent becomes IO: the single effects middleware, the optimistic-then-revert dance, and the
> offline-first queue that drains on reconnect.

## Rule E — effects originate only in middleware

The **only** place a dispatched action turns into IO is a middleware:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/EffectsMiddleware.kt → effectsMiddleware`.
Reducers and composables stay pure. Each feature that performs IO ships its own
`Middleware<ModelState>` and they compose into the per-account pipeline (`activityLogger → undo →
effects`, see [store-setup.md](./store-setup.md)) — "Rule E" is this discipline, not a single file.

All repository work runs on the per-account background `CoroutineScope` (off-main, `Dispatchers.Default`).
Dispatches that follow marshal back to main through the store's `NotificationContext`, so the effect
code does **no explicit main hop**.

**Lifecycle loads key on STATE, not navigation events.** A load triggered alongside a `Navigate`
dispatch never fires on state-only entry points — process-death restore, deep links, DevTools
time-travel, account-switch hydration — because those set state without replaying events. Key the
effect on the state slice instead (taskflow's board load keys on the nav-derived `activeBoardId`),
or match the hydrating action itself in middleware. See
[state-persistence.md](./state-persistence.md) ("Restore replays no events").

## The optimistic dance

For a mutating action the handler:

1. **Captures the inverse from the pre-update state** —
   `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/CardActions.kt → InverseOp`
   (`MoveBack`, `DeleteAdded`, `RestoreEdited`, `ReAddDeleted`). This must happen **before** `next(action)`
   while the old state is still visible.
2. **Runs the reducer optimistically** via `next(action)` (synchronous, under the write lock) so the UI
   updates instantly.
3. **Launches the repository call** on the background scope, carrying that captured inverse.

On a backend rejection the engine emits
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/CardActions.kt → CardOpFailed`
carrying the inverse, and the reducer reverts **exactly that op** — not a whole-board snapshot. On
acceptance it emits `CardOpSucceeded`, which clears the card from the in-flight ("Saving…") set.

## Offline-first — local-first write, queue, drain

`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/sync/SyncRepository.kt → SyncRepository`
is the gateway. Every mutation does three things:

1. Write `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/local/LocalStore.kt → LocalStore`
   immediately (durable, works offline).
2. Enqueue a sync op carrying its per-op inverse.
3. Kick `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/sync/SyncEngine.kt → SyncEngine`
   to attempt a drain.

`SyncEngine.drain` walks the pending-op queue against the remote and handles each outcome:

| Outcome | Action |
|---|---|
| Accepted | mark synced; emit `CardOpSucceeded` (clears in-flight) |
| Rejected | emit `CardOpFailed` with the inverse; drop the op (never retried) |
| Offline | stop the drain, **leave the queue intact**, retry on reconnect |
| Transient network error | increment the attempt counter, keep the op queued |

After a successful push it pulls remote changes since the last cursor and merges them. The queue is
durable, so going offline never reverts an optimistic change — offline is not a rejection.

## Rule F — delta-only status

`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/sync/SyncEngine.kt → SyncStatus`
(online · pendingCount · inFlight · lastSyncedAt · lastError) is emitted **only when it actually
changes** — no notification churn on a no-op drain. The status flows to a reducer slot the
`SyncIndicator`/`SyncToast` bind narrowly (Rule C).

## The remote seam

`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/remote/RemoteApi.kt → RemoteApi`
is the interface (`push(ops) → PushResult`, `pull(since) → page`). The demo backs it with a
`FakeRemoteApi` that simulates latency, a configurable failure rate, an offline gate, and deterministic
WIP-limit rejections — all seeded for reproducibility. `push` returns
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/infra/data/remote/PushResult.kt → PushResult`
(`Accepted` | `Rejected(opId, reason)`); connectivity failures are thrown, not modelled as results.
Swap this interface for a real HTTP client (via the `HttpEngine` shim, [platform-shims.md](./platform-shims.md))
without touching the middleware.

## Proof it works

`examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/infra/data/OfflineSyncE2ETest.kt → OfflineSyncE2ETest`
drives the real store + middleware + sync + SQLDelight under virtual time: an offline move persists and
queues without reverting; reconnect drains the queue and advances the cursor; a WIP-limit rejection
reverts via the per-op `MoveBack` inverse; an accepted move clears the in-flight set. The middleware in
isolation is covered by `EffectsMiddlewareTest`.

## Verify loop

`./gradlew :examples:taskflow:composeApp:jvmTest` (the sync E2E + middleware tests are JVM-hosted under
virtual time), then `./gradlew detektAll`. Full treatment → [testing.md](./testing.md).

## Pitfalls

- Capturing the inverse **after** `next(action)` reads post-mutation state — the revert will be wrong.
- Doing IO in a reducer or a composable instead of the middleware breaks Rule E and makes the write
  untestable as a pure function.
- Emitting status unconditionally floods subscribers — keep the Rule F delta check.
- Treating an offline error as a rejection wrongly reverts the optimistic change.

## See also

- [store-setup.md](./store-setup.md) — the middleware pipeline order this composes into.
- [feature-slice.md](./feature-slice.md) — a feature ships its effect handler only if it does IO.
- [README](./README.md)
