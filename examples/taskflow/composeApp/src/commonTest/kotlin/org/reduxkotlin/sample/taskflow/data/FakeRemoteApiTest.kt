package org.reduxkotlin.sample.taskflow.data

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.data.remote.FakeRemoteApi
import org.reduxkotlin.sample.taskflow.data.remote.InverseOpDto
import org.reduxkotlin.sample.taskflow.data.remote.OfflineException
import org.reduxkotlin.sample.taskflow.data.remote.PushResult
import org.reduxkotlin.sample.taskflow.data.remote.RemoteChange
import org.reduxkotlin.sample.taskflow.data.remote.SyncOp
import org.reduxkotlin.sample.taskflow.data.remote.TransientNetworkException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class FakeRemoteApiTest {

    private val annBoardId = BoardId("ann-board")
    private val todo = ColumnId("ann-todo")
    private val doing = ColumnId("ann-doing")

    private fun config(
        online: Boolean = true,
        failureRate: Float = 0f,
        latencyMinMs: Int = 100,
        latencyMaxMs: Int = 100,
    ) = org.reduxkotlin.sample.taskflow.core.FakeServiceConfig(
        latencyMinMs = latencyMinMs,
        latencyMaxMs = latencyMaxMs,
        failureRate = failureRate,
        online = online,
    )

    private fun api(
        config: () -> org.reduxkotlin.sample.taskflow.core.FakeServiceConfig,
        rng: Random = Random(0),
    ): FakeRemoteApi = FakeRemoteApi(seededAccounts = SeedData.seededAccounts(), config = config, rng = rng)

    // A normal move: card "ann-1" (in To Do) -> Done (no WIP limit). Should be Accepted.
    private fun normalMove(): SyncOp.Move = SyncOp.Move(
        opId = "op-normal",
        cardId = "ann-1",
        from = todo.v,
        to = ColumnId("ann-done").v,
        toIndex = 0,
        inverse = InverseOpDto.MoveBack(cardId = "ann-1", to = todo.v, index = 0),
    )

    // A conflict move: card "ann-1" -> Doing, whose seeded WIP limit (2) is already reached.
    private fun wipMove(): SyncOp.Move = SyncOp.Move(
        opId = "op-wip",
        cardId = "ann-1",
        from = todo.v,
        to = doing.v,
        toIndex = 0,
        inverse = InverseOpDto.MoveBack(cardId = "ann-1", to = todo.v, index = 0),
    )

    @Test
    fun offlinePushThrows() = runTest {
        val remote = api({ config(online = false) })
        assertFailsWith<OfflineException> { remote.push(listOf(normalMove())) }
    }

    @Test
    fun offlinePullThrows() = runTest {
        val remote = api({ config(online = false) })
        assertFailsWith<OfflineException> { remote.pull(since = null) }
    }

    @Test
    fun transientFailurePush() = runTest {
        val remote = api({ config(online = true, failureRate = 1f) })
        assertFailsWith<TransientNetworkException> { remote.push(listOf(normalMove())) }
    }

    @Test
    fun normalPushAccepted() = runTest {
        val remote = api({ config(online = true, failureRate = 0f) })
        val result = remote.push(listOf(normalMove()))
        assertEquals(PushResult.Accepted, result)
    }

    @Test
    fun wipMoveRejected() = runTest {
        val remote = api({ config(online = true, failureRate = 0f) })
        val result = remote.push(listOf(wipMove()))
        val rejected = assertIs<PushResult.Rejected>(result)
        assertEquals("op-wip", rejected.opId)
        assertTrue(rejected.reason.contains("WIP", ignoreCase = true))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun latencyHonored() = runTest {
        val remote = api({ config(online = true, failureRate = 0f, latencyMinMs = 500, latencyMaxMs = 500) })
        val deferred = async { remote.push(listOf(normalMove())) }
        // Before the latency elapses the push must not have completed.
        testScheduler.advanceTimeBy(400)
        testScheduler.runCurrent()
        assertTrue(deferred.isActive, "push should still be suspended before latency elapses")
        testScheduler.advanceTimeBy(200)
        testScheduler.runCurrent()
        assertEquals(PushResult.Accepted, deferred.await())
    }

    @Test
    fun pullEmptyEarlyReturnsCursor() = runTest {
        val remote = api({ config(online = true) })
        val page = remote.pull(since = SeedData.SEED_INSTANT)
        assertTrue(page.changes.isEmpty())
        assertEquals(SeedData.SEED_INSTANT, page.cursor)
    }

    @Test
    fun botEditSurfacesOnNextPull() = runTest {
        val remote = api({ config(online = true) })
        val editedAt = Instant.fromEpochMilliseconds(SeedData.SEED_INSTANT.toEpochMilliseconds() + 1_000)
        val card: Card = SeedData.seededAccounts()
            .first { it.owner.id == AccountId("ann") }
            .board.cards.getValue(CardId("ann-4"))
        remote.botEdit(
            change = RemoteChange.CardUpserted(card = card, columnId = doing, sortIndex = 0),
            at = editedAt,
        )
        val page = remote.pull(since = SeedData.SEED_INSTANT)
        assertEquals(1, page.changes.size)
        assertEquals(editedAt, page.cursor)
        // A pull that is already caught up returns nothing.
        val empty = remote.pull(since = editedAt)
        assertTrue(empty.changes.isEmpty())
    }
}
