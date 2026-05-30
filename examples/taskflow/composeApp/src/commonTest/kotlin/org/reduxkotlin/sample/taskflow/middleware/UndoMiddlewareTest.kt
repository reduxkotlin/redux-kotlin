package org.reduxkotlin.sample.taskflow.middleware

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.BoardRestored
import org.reduxkotlin.sample.taskflow.action.BotMovedCard
import org.reduxkotlin.sample.taskflow.action.CardMoveRequested
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.PushUndo
import org.reduxkotlin.sample.taskflow.action.Redo
import org.reduxkotlin.sample.taskflow.action.SetUndoModel
import org.reduxkotlin.sample.taskflow.action.Undo
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.Board
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.Column
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.OpId
import org.reduxkotlin.sample.taskflow.model.Route
import org.reduxkotlin.sample.taskflow.model.UndoModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class UndoMiddlewareTest {
    private val ann = AccountId("ann")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val todo = ColumnId("todo")
    private val doing = ColumnId("doing")

    private fun card(id: String) = Card(
        id = CardId(id),
        title = "card-$id",
        description = "",
        createdBy = ann,
        createdAt = now,
        updatedAt = now,
    )

    private fun board(id: String = "b1"): Board {
        val c1 = card("c1")
        return Board(
            boardId = BoardId(id),
            columns = persistentListOf(
                Column(todo, "Todo", persistentListOf(c1.id)),
                Column(doing, "Doing", persistentListOf()),
            ),
            cards = persistentMapOf(c1.id to c1),
        )
    }

    /** Records dispatches and `next` calls; state is fixed (the loaded board + undo stacks). */
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

    private fun storeFor(boardModel: BoardModel, undo: UndoModel = UndoModel()): RecordingStore =
        RecordingStore(ModelState.of(boardModel, undo))

    private fun chainFor(store: RecordingStore, forwarded: MutableList<Any>): (Any) -> Any {
        val mw = undoMiddleware()
        return mw(store)({ action ->
            forwarded += action
            action
        })
    }

    @Test
    fun pushesSnapshotBeforeUndoableAction() {
        val present = board()
        val store = storeFor(BoardModel(present))
        val forwarded = mutableListOf<Any>()
        val chain = chainFor(store, forwarded)

        chain(CardMoveRequested(CardId("c1"), todo, doing, 0, OpId("op1")))

        // The snapshot of the PRESENT board is dispatched before the mutation is forwarded.
        val push = assertIs<PushUndo>(store.dispatched.single())
        assertEquals(present, push.snapshot)
        // The undoable action still flows down the chain.
        assertTrue(forwarded.single() is CardMoveRequested, "undoable action is forwarded: $forwarded")
    }

    @Test
    fun doesNotPushForNonUndoableAction() {
        val store = storeFor(BoardModel(board()))
        val forwarded = mutableListOf<Any>()
        val chain = chainFor(store, forwarded)

        chain(Navigate(Route.Profile))

        assertTrue(store.dispatched.none { it is PushUndo }, "no snapshot for nav: ${store.dispatched}")
        assertTrue(forwarded.single() is Navigate, "nav still forwarded")
    }

    @Test
    fun doesNotPushForBotAction() {
        val store = storeFor(BoardModel(board()))
        val forwarded = mutableListOf<Any>()
        val chain = chainFor(store, forwarded)

        chain(BotMovedCard(CardId("c1"), doing, 0))

        assertTrue(store.dispatched.none { it is PushUndo }, "bot moves never enter undo: ${store.dispatched}")
        assertTrue(forwarded.single() is BotMovedCard, "bot move still forwarded")
    }

    @Test
    fun botActionDuringSessionLeavesStacksIntact() {
        val past = board("past")
        val store = storeFor(BoardModel(board()), UndoModel(past = persistentListOf(past)))
        val forwarded = mutableListOf<Any>()
        val chain = chainFor(store, forwarded)

        chain(BotMovedCard(CardId("c1"), doing, 0))

        // No PushUndo / SetUndoModel dispatched → the existing stacks are untouched.
        assertTrue(
            store.dispatched.none { it is PushUndo || it is SetUndoModel },
            "bot action must not touch undo stacks: ${store.dispatched}",
        )
    }

    @Test
    fun undoDispatchesBoardRestoredAndSetUndoModel() {
        val past = board("past")
        val present = board("present")
        val store = storeFor(BoardModel(present), UndoModel(past = persistentListOf(past)))
        val forwarded = mutableListOf<Any>()
        val chain = chainFor(store, forwarded)

        chain(Undo)

        // The new undo stacks are committed first, then the restored board is applied.
        val setUndo = assertIs<SetUndoModel>(store.dispatched[0])
        assertTrue(setUndo.model.past.isEmpty(), "past popped")
        assertEquals(persistentListOf(present), setUndo.model.future, "present pushed to future")
        val restored = assertIs<BoardRestored>(store.dispatched[1])
        assertEquals(past, restored.board)
        // Undo itself is not forwarded down the chain.
        assertTrue(forwarded.isEmpty(), "Undo is intercepted, not forwarded: $forwarded")
    }

    @Test
    fun undoOnEmptyStackIsNoOp() {
        val store = storeFor(BoardModel(board()), UndoModel())
        val forwarded = mutableListOf<Any>()
        val chain = chainFor(store, forwarded)

        chain(Undo)

        assertTrue(store.dispatched.isEmpty(), "empty undo dispatches nothing: ${store.dispatched}")
    }

    @Test
    fun redoDispatchesBoardRestoredAndSetUndoModel() {
        val future = board("future")
        val present = board("present")
        val store = storeFor(BoardModel(present), UndoModel(future = persistentListOf(future)))
        val forwarded = mutableListOf<Any>()
        val chain = chainFor(store, forwarded)

        chain(Redo)

        val setUndo = assertIs<SetUndoModel>(store.dispatched[0])
        assertTrue(setUndo.model.future.isEmpty(), "future popped")
        assertEquals(persistentListOf(present), setUndo.model.past, "present pushed to past")
        val restored = assertIs<BoardRestored>(store.dispatched[1])
        assertEquals(future, restored.board)
        assertTrue(forwarded.isEmpty(), "Redo is intercepted, not forwarded: $forwarded")
    }
}
