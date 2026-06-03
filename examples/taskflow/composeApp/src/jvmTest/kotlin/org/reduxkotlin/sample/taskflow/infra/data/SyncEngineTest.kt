package org.reduxkotlin.sample.taskflow.infra.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardOpFailed
import org.reduxkotlin.sample.taskflow.core.CardOpSucceeded
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.core.InverseOp
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.db.TaskFlowDb
import org.reduxkotlin.sample.taskflow.infra.SeedData
import org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.infra.data.local.SqlDelightLocalStore
import org.reduxkotlin.sample.taskflow.infra.data.remote.FakeRemoteApi
import org.reduxkotlin.sample.taskflow.infra.data.remote.InverseOpDto
import org.reduxkotlin.sample.taskflow.infra.data.remote.RemoteChange
import org.reduxkotlin.sample.taskflow.infra.data.remote.SyncOp
import org.reduxkotlin.sample.taskflow.infra.data.sync.SyncEngine
import org.reduxkotlin.sample.taskflow.infra.data.sync.SyncStatus
import org.reduxkotlin.sample.taskflow.infra.db.taskFlowDb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncEngineTest {

    private val annId = AccountId("ann")
    private val annBoardId = BoardId("ann-board")
    private val todo = ColumnId("ann-todo")
    private val doing = ColumnId("ann-doing")
    private val done = ColumnId("ann-done")

    // ---- harness ----

    private fun newLocal(): CountingLocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TaskFlowDb.Schema.synchronous().create(driver)
        return CountingLocalStore(SqlDelightLocalStore(taskFlowDb(driver)))
    }

    private fun newRemote(configRef: () -> FakeServiceConfig): FakeRemoteApi =
        FakeRemoteApi(seededAccounts = SeedData.seededAccounts(), config = configRef, rng = Random(0))

    private fun zeroLatency(online: Boolean = true, failureRate: Float = 0f) =
        FakeServiceConfig(latencyMinMs = 0, latencyMaxMs = 0, failureRate = failureRate, online = online)

    private fun engine(
        local: LocalStore,
        remote: FakeRemoteApi,
        scope: CoroutineScope,
        rejects: MutableList<CardOpFailed>,
        statuses: MutableList<SyncStatus>,
        accepts: MutableList<CardOpSucceeded> = mutableListOf(),
    ): SyncEngine = SyncEngine(
        local = local,
        remote = remote,
        scope = scope,
        onAccept = { accepts += it },
        onReject = { rejects += it },
        onStatus = { statuses += it },
    )

    // A move that the server accepts: ann-1 (To Do) -> Done (no WIP limit).
    private fun acceptedMove(opId: String = "op-accept"): SyncOp.Move = SyncOp.Move(
        opId = opId,
        cardId = "ann-1",
        from = todo.v,
        to = done.v,
        toIndex = 0,
        inverse = InverseOpDto.MoveBack(cardId = "ann-1", to = todo.v, index = 0),
    )

    // A move the server rejects: ann-1 -> Doing, whose seeded WIP limit (2) is already reached.
    private fun rejectedMove(opId: String = "op-wip"): SyncOp.Move = SyncOp.Move(
        opId = opId,
        cardId = "ann-1",
        from = todo.v,
        to = doing.v,
        toIndex = 0,
        inverse = InverseOpDto.MoveBack(cardId = "ann-1", to = todo.v, index = 0),
    )

    // ---- (1) accepted drain ----

    @Test
    fun acceptedDrainMarksSyncedAndAdvancesCursor() = runTest {
        val local = newLocal()
        var cfg = zeroLatency(online = true)
        val remote = newRemote { cfg }
        // A bot edit so the post-push pull returns a non-empty page and lastSyncedAt advances.
        val botAt = Instant.fromEpochMilliseconds(SeedData.SEED_INSTANT.toEpochMilliseconds() + 1_000)
        val edited = SeedData.seededAccounts().first { it.owner.id == annId }
            .board.cards.getValue(CardId("ann-3")).copy(title = "Bot touched")
        remote.botEdit(RemoteChange.CardUpserted(edited, doing, 0), botAt)

        val rejects = mutableListOf<CardOpFailed>()
        val statuses = mutableListOf<SyncStatus>()
        val eng = engine(local, remote, backgroundScope, rejects, statuses)

        local.ensureSeeded()
        local.enqueue(annId, acceptedMove())
        assertEquals(1, local.pendingOps(annId).size)

        eng.drain(annId)

        assertEquals(0, local.pendingOps(annId).size, "accepted op should be marked synced")
        assertTrue(rejects.isEmpty())
        assertEquals(botAt, local.lastSyncedAt(annId), "cursor advances from the pulled page")
        val last = statuses.last()
        assertEquals(0, last.pendingCount)
        assertTrue(last.online)
        assertNotNull(last.lastSyncedAt)
    }

    // ---- (1b) accepted drain reports a per-op CardOpSucceeded (clears inFlight / "Saving…") ----

    @Test
    fun acceptedDrainEmitsCardOpSucceeded() = runTest {
        val local = newLocal()
        var cfg = zeroLatency(online = true)
        val remote = newRemote { cfg }
        val rejects = mutableListOf<CardOpFailed>()
        val statuses = mutableListOf<SyncStatus>()
        val accepts = mutableListOf<CardOpSucceeded>()
        val eng = engine(local, remote, backgroundScope, rejects, statuses, accepts)

        local.ensureSeeded()
        local.enqueue(annId, acceptedMove("op-accept"))

        eng.drain(annId)

        assertEquals(1, accepts.size, "an accepted op reports exactly one CardOpSucceeded; got $accepts")
        val ok = accepts.single()
        assertEquals(OpId("op-accept"), ok.opId, "the ack carries the queued op's id")
        assertEquals(CardId("ann-1"), ok.cardId, "the ack carries the op's card id")
        assertTrue(rejects.isEmpty(), "an accepted op is not a rejection")
    }

    // ---- (2) offline: queue grows, no revert ----

    @Test
    fun offlineKeepsOpQueuedWithNoRevert() = runTest {
        val local = newLocal()
        var cfg = zeroLatency(online = false)
        val remote = newRemote { cfg }
        val rejects = mutableListOf<CardOpFailed>()
        val statuses = mutableListOf<SyncStatus>()
        val eng = engine(local, remote, backgroundScope, rejects, statuses)

        local.ensureSeeded()
        local.enqueue(annId, acceptedMove("op-1"))
        local.enqueue(annId, acceptedMove("op-2"))

        eng.drain(annId)

        assertEquals(2, local.pendingOps(annId).size, "offline ops stay queued")
        assertTrue(rejects.isEmpty(), "offline never dispatches a revert")
        assertNull(local.lastSyncedAt(annId), "no pull while offline")
        val last = statuses.last()
        assertEquals(2, last.pendingCount)
        assertTrue(!last.online)
        assertNotNull(last.lastError)
    }

    // ---- (3) toggle online then drain -> queue drains ----

    @Test
    fun togglingOnlineDrainsTheQueue() = runTest {
        val local = newLocal()
        var cfg = zeroLatency(online = false)
        val remote = newRemote { cfg }
        val rejects = mutableListOf<CardOpFailed>()
        val statuses = mutableListOf<SyncStatus>()
        val eng = engine(local, remote, backgroundScope, rejects, statuses)

        local.ensureSeeded()
        local.enqueue(annId, acceptedMove())
        eng.drain(annId)
        assertEquals(1, local.pendingOps(annId).size)

        cfg = zeroLatency(online = true)
        eng.kick(annId).join()

        assertEquals(0, local.pendingOps(annId).size, "reconnect drains the queue")
        assertTrue(rejects.isEmpty())
    }

    // ---- (4) rejected push -> CardOpFailed with matching opId + correct inverse subtype ----

    @Test
    fun rejectedPushReportsInverse() = runTest {
        val local = newLocal()
        var cfg = zeroLatency(online = true)
        val remote = newRemote { cfg }
        val rejects = mutableListOf<CardOpFailed>()
        val statuses = mutableListOf<SyncStatus>()
        val eng = engine(local, remote, backgroundScope, rejects, statuses)

        local.ensureSeeded()
        local.enqueue(annId, rejectedMove("op-wip"))

        eng.drain(annId)

        // Per-assert messages: kotlin-test collapses the assertion frame, so without these a
        // failure reports only the @Test line and the failing assertion is unidentifiable.
        assertEquals(1, rejects.size, "exactly one reject for the single WIP-rejected op; got $rejects")
        val failed = rejects.single()
        assertEquals(OpId("op-wip"), failed.opId, "reject carries the queued op's id")
        assertEquals(CardId("ann-1"), failed.cardId, "reject carries the moved card's id")
        val inverse = assertIs<InverseOp.MoveBack>(failed.inverse, "a Move rejects with a MoveBack inverse")
        assertEquals(CardId("ann-1"), inverse.cardId, "inverse targets the same card")
        assertEquals(todo, inverse.to, "inverse moves the card back to its source column")
        assertTrue(failed.error.contains("WIP", ignoreCase = true), "reject reason names WIP: ${failed.error}")
        // Rejected op is dropped (store reverts it) — queue empties.
        assertEquals(0, local.pendingOps(annId).size, "rejected op is dropped from the queue")
    }

    // ---- (5) transient failure -> op retained + attempts incremented ----

    @Test
    fun transientFailureRetainsOpAndIncrementsAttempts() = runTest {
        val local = newLocal()
        var cfg = zeroLatency(online = true, failureRate = 1f)
        val remote = newRemote { cfg }
        val rejects = mutableListOf<CardOpFailed>()
        val statuses = mutableListOf<SyncStatus>()
        val eng = engine(local, remote, backgroundScope, rejects, statuses)

        local.ensureSeeded()
        local.enqueue(annId, acceptedMove("op-flaky"))

        eng.drain(annId)

        assertEquals(1, local.pendingOps(annId).size, "transient failure keeps the op queued")
        assertTrue(rejects.isEmpty(), "a transient failure is not a rejection")
        assertEquals(1, local.incrementAttemptsCalls[OpId("op-flaky")], "attempts bumped once")
    }

    // ---- (6) pull merges remote changes into local ----

    @Test
    fun pullMergesRemoteChangesIntoLocal() = runTest {
        val local = newLocal()
        var cfg = zeroLatency(online = true)
        val remote = newRemote { cfg }
        val rejects = mutableListOf<CardOpFailed>()
        val statuses = mutableListOf<SyncStatus>()
        val eng = engine(local, remote, backgroundScope, rejects, statuses)

        local.ensureSeeded()
        // A bot edit waiting on the server; an accepted op so the engine reaches the pull step.
        val botAt = Instant.fromEpochMilliseconds(SeedData.SEED_INSTANT.toEpochMilliseconds() + 5_000)
        val edited = SeedData.seededAccounts().first { it.owner.id == annId }
            .board.cards.getValue(CardId("ann-2")).copy(title = "Remote rename")
        remote.botEdit(RemoteChange.CardUpserted(edited, todo, 0), botAt)
        local.enqueue(annId, acceptedMove())

        eng.drain(annId)

        val merged = local.loadBoard(annBoardId)!!.cards.getValue(CardId("ann-2"))
        assertEquals("Remote rename", merged.title, "pulled change merged into local")
        assertEquals(botAt, local.lastSyncedAt(annId))
    }
}

/**
 * A [LocalStore] decorator that delegates to [delegate] but records how often
 * [incrementAttempts] is called per [OpId] — the only signal the public contract does not
 * otherwise expose for the transient-retry assertion.
 */
private class CountingLocalStore(private val delegate: LocalStore) : LocalStore by delegate {
    val incrementAttemptsCalls: MutableMap<OpId, Int> = mutableMapOf()

    override suspend fun incrementAttempts(opId: OpId) {
        incrementAttemptsCalls[opId] = (incrementAttemptsCalls[opId] ?: 0) + 1
        delegate.incrementAttempts(opId)
    }
}
