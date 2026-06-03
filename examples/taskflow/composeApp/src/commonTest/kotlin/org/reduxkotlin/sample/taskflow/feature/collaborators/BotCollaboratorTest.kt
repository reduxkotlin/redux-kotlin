package org.reduxkotlin.sample.taskflow.feature.collaborators

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.BotAddedCard
import org.reduxkotlin.sample.taskflow.action.BotMovedCard
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.model.BoardModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BotCollaboratorTest {
    private val ann = AccountId("ann")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val todo = ColumnId("todo")
    private val doing = ColumnId("doing")
    private val done = ColumnId("done")
    private val intervalMs = 4_000

    private fun card(id: String) = Card(
        id = CardId(id),
        title = "card-$id",
        description = "",
        createdBy = ann,
        createdAt = now,
        updatedAt = now,
    )

    // Three columns; To Do + Doing hold cards (movable), Done is the terminal column.
    private fun board(): Board {
        val c1 = card("c1")
        val c2 = card("c2")
        val c3 = card("c3")
        return Board(
            boardId = BoardId("b1"),
            columns = persistentListOf(
                Column(todo, "To Do", persistentListOf(c1.id, c2.id)),
                Column(doing, "Doing", persistentListOf(c3.id)),
                Column(done, "Done", persistentListOf()),
            ),
            cards = persistentMapOf(c1.id to c1, c2.id to c2, c3.id to c3),
        )
    }

    /** Records dispatches; state is a fixed loaded board so the bot can pick a real card. */
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

    private fun storeFor(b: Board = board()): RecordingStore = RecordingStore(ModelState.of(BoardModel(b)))

    private fun config(enabled: Boolean) = FakeServiceConfig(botEnabled = enabled, botIntervalMs = intervalMs)

    @Test
    fun dispatchesExactlyOneBotMutationPerInterval() = runTest {
        val store = storeFor()
        startBot(backgroundScope, store, { config(enabled = true) }, rngSeed = 1L)

        advanceTimeBy(intervalMs.toLong())
        runCurrent()

        val botActions = store.dispatched.filter { it is BotMovedCard || it is BotAddedCard }
        assertEquals(1, botActions.size, "exactly one bot mutation after one interval: ${store.dispatched}")
    }

    @Test
    fun botMovedCardTargetsARealLoadedCard() = runTest {
        val store = storeFor()
        startBot(backgroundScope, store, { config(enabled = true) }, rngSeed = 1L)

        advanceTimeBy(intervalMs.toLong())
        runCurrent()

        val move = store.dispatched.filterIsInstance<BotMovedCard>().single()
        // The bot moves a real card from the loaded board into one of its columns.
        assertTrue(board().cards.containsKey(move.cardId), "bot moved a real loaded card: ${move.cardId}")
        assertTrue(
            board().columns.any { it.id == move.to },
            "bot target column is a real column: ${move.to}",
        )
        // It moves a card forward off a non-Done column (never targets the same terminal nowhere).
        assertTrue(move.to != todo || move.toIndex >= 0, "valid target index")
    }

    @Test
    fun disabledBotNeverDispatches() = runTest {
        val store = storeFor()
        startBot(backgroundScope, store, { config(enabled = false) }, rngSeed = 1L)

        // Advance several intervals — a disabled bot must stay silent the whole time.
        repeat(5) { advanceTimeBy(intervalMs.toLong()) }
        runCurrent()

        val botActions = store.dispatched.filter { it is BotMovedCard || it is BotAddedCard }
        assertTrue(botActions.isEmpty(), "disabled bot dispatches nothing: ${store.dispatched}")
    }

    @Test
    fun cancellingJobStopsFurtherDispatches() = runTest {
        val store = storeFor()
        val job = startBot(backgroundScope, store, { config(enabled = true) }, rngSeed = 1L)

        advanceTimeBy(intervalMs.toLong())
        runCurrent()
        val afterFirst = store.dispatched.count { it is BotMovedCard || it is BotAddedCard }
        assertEquals(1, afterFirst, "one mutation before cancel")

        job.cancel()
        repeat(5) { advanceTimeBy(intervalMs.toLong()) }
        runCurrent()

        val total = store.dispatched.count { it is BotMovedCard || it is BotAddedCard }
        assertEquals(afterFirst, total, "no further dispatches after cancel: ${store.dispatched}")
    }

    @Test
    fun noBoardLoadedSkipsDispatch() = runTest {
        val store = RecordingStore(ModelState.of(BoardModel(null)))
        startBot(backgroundScope, store, { config(enabled = true) }, rngSeed = 1L)

        advanceTimeBy(intervalMs.toLong())
        runCurrent()

        val botActions = store.dispatched.filter { it is BotMovedCard || it is BotAddedCard }
        assertTrue(botActions.isEmpty(), "no board -> no bot dispatch: ${store.dispatched}")
    }
}
