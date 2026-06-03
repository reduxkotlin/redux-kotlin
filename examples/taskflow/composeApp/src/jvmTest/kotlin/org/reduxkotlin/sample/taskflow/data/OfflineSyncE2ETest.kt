package org.reduxkotlin.sample.taskflow.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.Store
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.LoadBoardRequested
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.SetFailureRate
import org.reduxkotlin.sample.taskflow.action.SetLatency
import org.reduxkotlin.sample.taskflow.action.SetOnline
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.data.remote.FakeRemoteApi
import org.reduxkotlin.sample.taskflow.data.remote.RemoteChange
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.db.taskFlowDb
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.SyncModel
import org.reduxkotlin.sample.taskflow.model.columnById
import org.reduxkotlin.sample.taskflow.store.AccountStoreHandle
import org.reduxkotlin.sample.taskflow.store.createAccountStore
import org.reduxkotlin.sample.taskflow.store.createAppStore
import org.reduxkotlin.sample.taskflow.store.getModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * End-to-end offline-sync proof (plan Task 36): the **real** production wiring — the root app store
 * ([createAppStore]), a per-account store ([createAccountStore]) with its effects/undo/activity
 * middleware, its [org.reduxkotlin.sample.taskflow.data.sync.SyncRepository] +
 * [org.reduxkotlin.sample.taskflow.data.sync.SyncEngine] over an isolated [FakeRemoteApi], and a
 * durable in-memory [LocalStore] — exercised across the three real transitions:
 *
 * 1. **Offline** — the move persists locally and queues; no revert (offline is not a rejection).
 * 2. **Reconnect** — the queue drains, the cursor advances, the board is unchanged.
 * 3. **Rejected** — a move into an at-WIP-limit column is reverted via the op's **per-op inverse**
 *    (the card returns to its source column), not a whole-board snapshot.
 *
 * The account store's background scope is overridden with the test's [runTest] scope so the whole
 * effect → sync → reject chain runs under virtual time and [settle] drives it to quiescence.
 * [NotificationContext.Inline] keeps subscriber callbacks synchronous on the dispatching thread so a
 * dispatch's model write is visible the moment [settle] returns. The root [SetOnline] is what the
 * [FakeRemoteApi]'s connectivity gate reads; the account-store [SetOnline]/[LoadBoardRequested] is
 * what the effects middleware turns into a sync kick / board load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSyncE2ETest {

    private val annId = AccountId("ann")
    private val annBoardId = BoardId("ann-board")
    private val todo = ColumnId("ann-todo")
    private val doing = ColumnId("ann-doing")
    private val done = ColumnId("ann-done")

    // ---- harness ----

    private fun newLocal(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    // The whole live wiring for one account, all on the test scope (so virtual time controls it).
    private class Wiring(val rootStore: Store<ModelState>, val handle: AccountStoreHandle, val local: LocalStore) {
        val accountStore: Store<ModelState> get() = handle.store
        val remote: FakeRemoteApi get() = handle.remoteApi as FakeRemoteApi
    }

    private suspend fun wire(local: LocalStore, scope: CoroutineScope): Wiring {
        local.ensureSeeded()
        // Inline notification + the test scope: deterministic, virtual-time-driven, no main hop.
        val rootStore = createAppStore(NotificationContext.Inline)
        // Zero latency + no transient failures: the only failure path under test is the deterministic
        // WIP-limit rejection (the FakeRemoteApi reads this live config from the root store).
        rootStore.dispatch(SetLatency(0, 0))
        rootStore.dispatch(SetFailureRate(0f))
        val handle = createAccountStore(
            detail = SeedData.accountDetail(annId),
            rootStore = rootStore,
            localStore = local,
            notificationContext = NotificationContext.Inline,
            scope = scope,
        )
        return Wiring(rootStore, handle, local)
    }

    // Drives the effect/sync coroutines launched on the test scope to quiescence. runCurrent() first
    // dispatches the freshly-launched effect tasks; advanceUntilIdle() then runs any virtual-time work
    // (a chained drain → reject → dispatch). Repeated so a multi-hop chain (move → push-reject →
    // CardOpFailed dispatch → revert) fully settles.
    private fun TestScope.settle() {
        repeat(3) {
            runCurrent()
            advanceUntilIdle()
        }
    }

    // Loads the seeded board into the account store via the real effect path (LoadBoardRequested ->
    // the effects middleware reads the LocalStore and dispatches LoadBoardSucceeded) so the effects
    // middleware can compute inverses and the board reducer has a non-null board to mutate.
    private fun TestScope.openBoard(wiring: Wiring) {
        wiring.accountStore.dispatch(Navigate(Route.Board(annBoardId)))
        wiring.accountStore.dispatch(LoadBoardRequested(annBoardId))
        settle()
    }

    private fun boardOf(store: Store<ModelState>): Board? = store.getModel<BoardModel>().board
    private fun syncOf(store: Store<ModelState>): SyncModel = store.getModel<SyncModel>()
    private fun cardColumn(store: Store<ModelState>, cardId: CardId): ColumnId? =
        boardOf(store)!!.columns.firstOrNull { it.cardIds.contains(cardId) }?.id

    // ---- (1+2) offline move persists + queues + no revert, then reconnect drains ----

    @Test
    fun offlineMovePersistsQueuesAndNeverReverts_thenReconnectDrains() = runTest {
        val local = newLocal()
        val wiring = wire(local, backgroundScope)
        // A bot edit waiting on the server so the post-reconnect pull returns a non-empty page and
        // lastSyncedAt advances (an empty pull echoes the cursor and would leave it null).
        val botAt = Instant.fromEpochMilliseconds(SeedData.SEED_INSTANT.toEpochMilliseconds() + 1_000)
        val edited: Card = SeedData.seededAccounts().first { it.owner.id == annId }
            .board.cards.getValue(CardId("ann-3")).copy(title = "Bot touched")
        wiring.remote.botEdit(RemoteChange.CardUpserted(edited, doing, 0), botAt)

        openBoard(wiring)
        assertNotNull(boardOf(wiring.accountStore), "board loads into the account store")

        // ---- OFFLINE: move ann-1 To Do -> Done while the backend is unreachable ----
        wiring.rootStore.dispatch(SetOnline(false))
        assertTrue(!wiring.rootStore.getModel<AppSettingsModel>().fakeService.online, "root settings went offline")

        wiring.accountStore.dispatch(CardMoveRequested(CardId("ann-1"), todo, done, 0, OpId("op-offline")))
        settle()

        // Durable: the move hit the LocalStore and survives offline.
        val durable = wiring.local.loadBoard(annBoardId)!!
        assertTrue(
            durable.columnById(done)!!.cardIds.contains(CardId("ann-1")),
            "the move is durable in the LocalStore while offline",
        )
        assertTrue(
            !durable.columnById(todo)!!.cardIds.contains(CardId("ann-1")),
            "the card left To Do in the durable cache",
        )
        // Queued: the op could not push, so it stays pending.
        assertTrue(syncOf(wiring.accountStore).pendingCount > 0, "the offline op is queued (pendingCount > 0)")
        assertEquals(1, wiring.local.pendingOps(annId).size, "exactly one op queued in the durable outbox")
        // No revert: offline is never a rejection — the store's board still shows the move.
        assertEquals(
            done,
            cardColumn(wiring.accountStore, CardId("ann-1")),
            "no revert while offline — card stays in Done",
        )
        assertTrue(!syncOf(wiring.accountStore).online, "the projected status reflects offline")

        // ---- RECONNECT: flip online on root (FakeRemoteApi reads it) + on the account store (kicks sync) ----
        wiring.rootStore.dispatch(SetOnline(true))
        wiring.accountStore.dispatch(SetOnline(true))
        settle()

        assertEquals(0, syncOf(wiring.accountStore).pendingCount, "reconnect drained the queue (pendingCount == 0)")
        assertEquals(0, wiring.local.pendingOps(annId).size, "the durable outbox is empty after the drain")
        assertNotNull(syncOf(wiring.accountStore).lastSyncedAt, "a successful sync set lastSyncedAt")
        assertEquals(botAt, wiring.local.lastSyncedAt(annId), "the sync cursor advanced from the pulled page")
        // Unchanged: the accepted move is still applied (still in Done, never reverted).
        assertEquals(
            done,
            cardColumn(wiring.accountStore, CardId("ann-1")),
            "the synced move is still applied after reconnect",
        )
        assertTrue(syncOf(wiring.accountStore).online, "online after the reconnect drain")
    }

    // ---- (3) rejected move into a full column reverts via the per-op inverse ----

    @Test
    fun rejectedMoveIntoFullColumnRevertsViaPerOpInverse() = runTest {
        val local = newLocal()
        val wiring = wire(local, backgroundScope)
        // Online, no transient failures: the only failure is the deterministic WIP-limit conflict.
        wiring.rootStore.dispatch(SetOnline(true))

        openBoard(wiring)

        // ann-1 starts in To Do; Doing is seeded at its WIP limit (2: ann-3, ann-4).
        assertEquals(todo, cardColumn(wiring.accountStore, CardId("ann-1")), "ann-1 starts in To Do")
        val doingBefore = boardOf(wiring.accountStore)!!.columnById(doing)!!
        assertEquals(doingBefore.cardIds.size, doingBefore.wipLimit, "Doing is seeded at its WIP limit")

        // ---- REJECTED: move ann-1 INTO the full Doing column (online, failureRate 0) ----
        wiring.accountStore.dispatch(CardMoveRequested(CardId("ann-1"), todo, doing, 0, OpId("op-wip")))
        settle()

        // The server rejected the move; the store processed a CardOpFailed and reverted via the op's
        // per-op MoveBack inverse — the card is back in its SOURCE column (not a whole-board snapshot).
        assertEquals(
            todo,
            cardColumn(wiring.accountStore, CardId("ann-1")),
            "the rejected move reverted via the per-op inverse — ann-1 is back in To Do",
        )
        assertTrue(
            !boardOf(wiring.accountStore)!!.columnById(doing)!!.cardIds.contains(CardId("ann-1")),
            "ann-1 is no longer in the full Doing column after the revert",
        )
        // The op was dropped from the queue (a rejection is never retried) and the card left in-flight.
        assertEquals(0, wiring.local.pendingOps(annId).size, "the rejected op was dropped from the outbox")
        assertEquals(0, syncOf(wiring.accountStore).pendingCount, "no ops pending after the rejection")
        assertTrue(
            !syncOf(wiring.accountStore).inFlight.contains(CardId("ann-1")),
            "ann-1 cleared from inFlight after CardOpFailed",
        )
        assertNotNull(boardOf(wiring.accountStore)!!.cards[CardId("ann-1")], "the reverted card is still on the board")
    }

    // ---- (4) accepted op clears the card from inFlight (the per-card "Saving…" state) ----

    @Test
    fun acceptedMoveClearsInFlightOnSuccess() = runTest {
        val local = newLocal()
        val wiring = wire(local, backgroundScope)
        // Online, zero latency, no transient failures: the move is deterministically accepted.
        wiring.rootStore.dispatch(SetOnline(true))

        openBoard(wiring)

        // Move ann-1 To Do -> Done (no WIP limit) — the server accepts it.
        wiring.accountStore.dispatch(CardMoveRequested(CardId("ann-1"), todo, done, 0, OpId("op-ok")))
        settle()

        assertEquals(0, wiring.local.pendingOps(annId).size, "the accepted op drained from the outbox")
        assertEquals(
            done,
            cardColumn(wiring.accountStore, CardId("ann-1")),
            "the accepted move stuck (no revert)",
        )
        // The fix: a CardOpSucceeded from the accepted push clears the card from inFlight so the
        // "Saving…" chip stops. Before the fix this stays true forever (success never cleared it).
        assertTrue(
            !syncOf(wiring.accountStore).inFlight.contains(CardId("ann-1")),
            "ann-1 cleared from inFlight after the op was accepted",
        )
    }
}
