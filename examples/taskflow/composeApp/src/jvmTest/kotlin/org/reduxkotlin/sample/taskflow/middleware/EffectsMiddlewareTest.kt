package org.reduxkotlin.sample.taskflow.middleware

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.CardOpFailed
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.core.InverseOp
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.data.SeedData
import org.reduxkotlin.sample.taskflow.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.data.remote.FakeRemoteApi
import org.reduxkotlin.sample.taskflow.data.sync.SyncRepository
import org.reduxkotlin.sample.taskflow.data.sync.SyncStatus
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.db.taskFlowDb
import org.reduxkotlin.sample.taskflow.model.BoardModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EffectsMiddlewareTest {

    private val annId = AccountId("ann")
    private val annBoardId = BoardId("ann-board")
    private val todo = ColumnId("ann-todo")
    private val doing = ColumnId("ann-doing")
    private val done = ColumnId("ann-done")
    private val card = CardId("ann-1")

    // ---- harness ----

    private fun newLocal(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return SqlDelightLocalStore(taskFlowDb(driver))
    }

    private fun newRemote(configRef: () -> FakeServiceConfig): FakeRemoteApi =
        FakeRemoteApi(seededAccounts = SeedData.seededAccounts(), config = configRef, rng = Random(0))

    private fun zeroLatency(online: Boolean = true, failureRate: Float = 0f) =
        FakeServiceConfig(latencyMinMs = 0, latencyMaxMs = 0, failureRate = failureRate, online = online)

    private fun repo(local: LocalStore, remote: FakeRemoteApi, scope: CoroutineScope): SyncRepository =
        SyncRepository(local = local, remote = remote, scope = scope, accountId = annId)

    // A recording store whose state holds the open board so the middleware can compute inverses.
    private class RecordingStore(initial: ModelState) : Store<ModelState> {
        val dispatched: MutableList<Any> = mutableListOf()
        private val current: ModelState = initial
        override var dispatch: (Any) -> Any = { action ->
            dispatched += action
            action
        }
        override val subscribe: (subscriber: () -> Unit) -> () -> Unit = { { } }
        override var replaceReducer: ((ModelState, Any) -> ModelState) -> Unit = { }
        override val getState: () -> ModelState = { current }
        override val store: Store<ModelState> get() = this
    }

    private suspend fun openBoard(local: LocalStore): Board {
        local.ensureSeeded()
        return local.loadBoard(annBoardId)!!
    }

    private fun storeFor(board: Board): RecordingStore = RecordingStore(
        ModelState.of(
            BoardModel(board),
            NavModel(persistentListOf(Route.BoardList, Route.Board(annBoardId))),
        ),
    )

    // ---- (1) rejected move -> CardOpFailed with matching opId + MoveBack inverse ----

    @Test
    fun rejectedMoveSurfacesCardOpFailedWithInverse() = runTest {
        val local = newLocal()
        val cfg = zeroLatency(online = true)
        val remote = newRemote { cfg }
        val syncRepo = repo(local, remote, backgroundScope)
        val board = openBoard(local)
        val store = storeFor(board)

        // Eagerly collect the repository's reject events so the emission is observed deterministically
        // (the effects middleware forwards exactly these to store.dispatch; collecting here avoids the
        // lazy-subscription race the middleware's own collector has under the test dispatcher).
        val rejects = mutableListOf<CardOpFailed>()
        backgroundScope.launch { syncRepo.rejectEvents.collect { rejects += it } }
        runCurrent()

        val mw = effectsMiddleware(syncRepo, backgroundScope)
        // Middleware is curried: mw(store)(next) -> (action). `next` records the optimistic action.
        val chain = mw(store)({ a -> store.dispatch(a) })

        val opId = OpId("op-wip")
        // ann-1 -> Doing reaches the seeded WIP limit (2) so the fake backend rejects it.
        chain(CardMoveRequested(cardId = card, from = todo, to = doing, toIndex = 0, opId = opId))
        // runCurrent() starts the background coroutine the middleware just launched for the sync write;
        // advanceUntilIdle() then drives the drain + reject emission to completion.
        runCurrent()
        advanceUntilIdle()

        // The optimistic action went through the chain to the base dispatcher.
        assertTrue(
            store.dispatched.any { it is CardMoveRequested && it.opId == opId },
            "the optimistic move is dispatched before sync runs: ${store.dispatched}",
        )
        // The rejected op is dropped from the queue (the store reverts it via the inverse).
        assertEquals(0, local.pendingOps(annId).size, "rejected op dropped from queue")
        val failed = rejects.single()
        assertEquals(opId, failed.opId, "reject carries the original opId")
        assertEquals(card, failed.cardId, "reject carries the moved card id")
        val inverse = assertIs<InverseOp.MoveBack>(failed.inverse, "a Move rejects with a MoveBack inverse")
        assertEquals(card, inverse.cardId, "inverse targets the same card")
        assertEquals(todo, inverse.to, "inverse moves the card back to its source column")
        assertEquals(0, inverse.index, "inverse restores the card's original index in To Do")
    }

    // ---- (2) accepted move -> drained queue surfaces a status with pendingCount 0 ----

    @Test
    fun acceptedMoveDrainsQueueAndSurfacesEmptyStatus() = runTest {
        val local = newLocal()
        val cfg = zeroLatency(online = true)
        val remote = newRemote { cfg }
        val syncRepo = repo(local, remote, backgroundScope)
        val board = openBoard(local)
        val store = storeFor(board)

        // Eagerly collect status + rejects so the post-drain emissions are observed deterministically.
        val statuses = mutableListOf<SyncStatus>()
        val rejects = mutableListOf<CardOpFailed>()
        backgroundScope.launch { syncRepo.status.collect { statuses += it } }
        backgroundScope.launch { syncRepo.rejectEvents.collect { rejects += it } }
        runCurrent()

        val mw = effectsMiddleware(syncRepo, backgroundScope)
        val chain = mw(store)({ a -> store.dispatch(a) })

        // ann-1 -> Done has no WIP limit, so the backend accepts and the queue drains.
        chain(CardMoveRequested(cardId = card, from = todo, to = done, toIndex = 0, opId = OpId("op-accept")))
        runCurrent()
        advanceUntilIdle()

        assertTrue(rejects.isEmpty(), "an accepted move never reverts: $rejects")
        // The queue drained (op pushed + marked synced), and the projected status reflects the empty queue.
        assertEquals(0, local.pendingOps(annId).size, "the accepted op was marked synced and removed")
        val status = statuses.last { it.pendingCount == 0 && it.lastError == null }
        assertEquals(0, status.pendingCount, "the accepted op drained off the queue")
        assertTrue(status.online, "still online after a successful drain")
    }
}
