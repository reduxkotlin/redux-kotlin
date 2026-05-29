package org.reduxkotlin.sample.taskflow.reducer

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.reduxkotlin.sample.taskflow.action.AddCard
import org.reduxkotlin.sample.taskflow.action.BoardClosed
import org.reduxkotlin.sample.taskflow.action.BoardRestored
import org.reduxkotlin.sample.taskflow.action.BotAddedCard
import org.reduxkotlin.sample.taskflow.action.BotMovedCard
import org.reduxkotlin.sample.taskflow.action.CardMoveRequested
import org.reduxkotlin.sample.taskflow.action.CardOpFailed
import org.reduxkotlin.sample.taskflow.action.CardOpSucceeded
import org.reduxkotlin.sample.taskflow.action.DeleteCard
import org.reduxkotlin.sample.taskflow.action.EditCard
import org.reduxkotlin.sample.taskflow.action.InverseOp
import org.reduxkotlin.sample.taskflow.action.LoadBoardSucceeded
import org.reduxkotlin.sample.taskflow.action.RecordActivity
import org.reduxkotlin.sample.taskflow.action.Redo
import org.reduxkotlin.sample.taskflow.action.Refresh
import org.reduxkotlin.sample.taskflow.action.SetFilterAssignee
import org.reduxkotlin.sample.taskflow.action.SetFilterQuery
import org.reduxkotlin.sample.taskflow.action.SyncStatusChanged
import org.reduxkotlin.sample.taskflow.action.ToggleFilterLabel
import org.reduxkotlin.sample.taskflow.action.Undo
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.ActivityEntry
import org.reduxkotlin.sample.taskflow.model.ActivityModel
import org.reduxkotlin.sample.taskflow.model.Board
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.Column
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.FilterModel
import org.reduxkotlin.sample.taskflow.model.LabelId
import org.reduxkotlin.sample.taskflow.model.OpId
import org.reduxkotlin.sample.taskflow.model.SyncModel
import org.reduxkotlin.sample.taskflow.model.UndoModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class BoardReducersTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val later = Instant.fromEpochMilliseconds(1_700_000_500_000)
    private val ann = AccountId("ann")

    private val todo = ColumnId("todo")
    private val doing = ColumnId("doing")
    private val done = ColumnId("done")

    private fun card(id: String) = Card(
        id = CardId(id),
        title = "card-$id",
        description = "",
        createdBy = ann,
        createdAt = now,
        updatedAt = now,
    )

    /** todo=[c1,c2] doing=[c3] done=[]; cards keyed for all three. */
    private fun board(): Board {
        val c1 = card("c1")
        val c2 = card("c2")
        val c3 = card("c3")
        return Board(
            boardId = BoardId("b1"),
            columns = persistentListOf(
                Column(todo, "Todo", persistentListOf(c1.id, c2.id)),
                Column(doing, "Doing", persistentListOf(c3.id)),
                Column(done, "Done", persistentListOf()),
            ),
            cards = persistentMapOf(c1.id to c1, c2.id to c2, c3.id to c3),
        )
    }

    private fun Board.column(id: ColumnId): Column = columns.first { it.id == id }

    /** integrity invariant (e): cards.keys == union of all column cardIds, no duplicates. */
    private fun assertIntegrity(b: Board) {
        val all = b.columns.flatMap { it.cardIds }
        assertEquals(all.size, all.toSet().size, "duplicate cardIds across columns")
        assertEquals(b.cards.keys, all.toSet(), "cards.keys must equal union of column cardIds")
    }

    // --- (a) move integrity-preserving ---

    @Test
    fun moveUpdatesExactlyTwoColumnsAndPreservesIntegrity() {
        val start = BoardModel(board())
        val next = boardReducer(start, CardMoveRequested(CardId("c1"), todo, doing, 0, OpId("op1")), ann)
        val b = next.board!!
        assertEquals(persistentListOf(CardId("c2")), b.column(todo).cardIds)
        assertEquals(persistentListOf(CardId("c1"), CardId("c3")), b.column(doing).cardIds)
        assertEquals(persistentListOf(), b.column(done).cardIds)
        assertIntegrity(b)
    }

    @Test
    fun moveWithStaleFromDoesNotOrphanRemovesFromAllColumns() {
        // 'from' lies (says done) but card actually in todo; removing from ALL columns prevents orphan/dup.
        val start = BoardModel(board())
        val next = boardReducer(start, CardMoveRequested(CardId("c1"), done, doing, 0, OpId("op1")), ann)
        val b = next.board!!
        assertFalse(b.column(todo).cardIds.contains(CardId("c1")))
        assertTrue(b.column(doing).cardIds.contains(CardId("c1")))
        assertIntegrity(b)
    }

    @Test
    fun moveClampsIndex() {
        val start = BoardModel(board())
        val next = boardReducer(start, CardMoveRequested(CardId("c3"), doing, todo, 99, OpId("op1")), ann)
        val b = next.board!!
        assertEquals(persistentListOf(CardId("c1"), CardId("c2"), CardId("c3")), b.column(todo).cardIds)
        assertIntegrity(b)
    }

    @Test
    fun moveDoesNotChangeUpdatedAt() {
        val start = BoardModel(board())
        val next = boardReducer(start, CardMoveRequested(CardId("c1"), todo, doing, 0, OpId("op1")), ann)
        assertEquals(now, next.board!!.cards[CardId("c1")]!!.updatedAt)
    }

    // --- (b) load / close ---

    @Test
    fun loadBoardSucceededLoadsFromNotLoaded() {
        val b = board()
        val next = boardReducer(BoardModel(null), LoadBoardSucceeded(b), ann)
        assertEquals(BoardModel(b), next)
    }

    @Test
    fun boardClosedResetsToNotLoaded() {
        val next = boardReducer(BoardModel(board()), BoardClosed, ann)
        assertEquals(BoardModel(null), next)
    }

    @Test
    fun whenBoardNullMutationsAreNoOp() {
        val start = BoardModel(null)
        assertSame(start, boardReducer(start, CardMoveRequested(CardId("c1"), todo, doing, 0, OpId("op1")), ann))
    }

    @Test
    fun boardReducerReturnsSameInstanceForUnhandled() {
        val start = BoardModel(board())
        assertSame(start, boardReducer(start, Refresh, ann))
    }

    // --- addCard ---

    @Test
    fun addCardCreatesCardAndAppendsToColumn() {
        val start = BoardModel(board())
        val next = boardReducer(
            start,
            AddCard(done, CardId("c9"), "New", "Desc", OpId("op1"), now),
            ann,
        )
        val b = next.board!!
        val created = b.cards[CardId("c9")]!!
        assertEquals("New", created.title)
        assertEquals("Desc", created.description)
        assertEquals(ann, created.createdBy)
        assertEquals(now, created.createdAt)
        assertEquals(now, created.updatedAt)
        assertNull(created.assigneeId)
        assertTrue(created.attachments.isEmpty())
        assertTrue(created.labels.isEmpty())
        assertEquals(persistentListOf(CardId("c9")), b.column(done).cardIds)
        assertIntegrity(b)
    }

    // --- editCard ---

    @Test
    fun editCardUpdatesTitleDescriptionAndUpdatedAt() {
        val start = BoardModel(board())
        val next = boardReducer(start, EditCard(CardId("c1"), "T2", "D2", OpId("op1"), later), ann)
        val b = next.board!!
        val edited = b.cards[CardId("c1")]!!
        assertEquals("T2", edited.title)
        assertEquals("D2", edited.description)
        assertEquals(later, edited.updatedAt)
        assertIntegrity(b)
    }

    // --- deleteCard ---

    @Test
    fun deleteCardRemovesFromCardsAndColumn() {
        val start = BoardModel(board())
        val next = boardReducer(start, DeleteCard(CardId("c1"), OpId("op1")), ann)
        val b = next.board!!
        assertFalse(b.cards.containsKey(CardId("c1")))
        assertFalse(b.column(todo).cardIds.contains(CardId("c1")))
        assertIntegrity(b)
    }

    // --- (c) CardOpFailed applies its inverse ---

    @Test
    fun cardOpFailedMoveBackMovesCardBack() {
        // simulate optimistic move c1 todo->doing, then revert via MoveBack to todo@0
        val moved = boardReducer(BoardModel(board()), CardMoveRequested(CardId("c1"), todo, doing, 0, OpId("op1")), ann)
        val reverted = boardReducer(
            moved,
            CardOpFailed(OpId("op1"), CardId("c1"), "boom", InverseOp.MoveBack(CardId("c1"), todo, 0)),
            ann,
        )
        val b = reverted.board!!
        assertEquals(persistentListOf(CardId("c1"), CardId("c2")), b.column(todo).cardIds)
        assertFalse(b.column(doing).cardIds.contains(CardId("c1")))
        assertIntegrity(b)
    }

    @Test
    fun cardOpFailedDeleteAddedDeletesCard() {
        val added = boardReducer(
            BoardModel(board()),
            AddCard(done, CardId("c9"), "New", "", OpId("op1"), now),
            ann,
        )
        val reverted = boardReducer(
            added,
            CardOpFailed(OpId("op1"), CardId("c9"), "boom", InverseOp.DeleteAdded(CardId("c9"))),
            ann,
        )
        val b = reverted.board!!
        assertFalse(b.cards.containsKey(CardId("c9")))
        assertFalse(b.column(done).cardIds.contains(CardId("c9")))
        assertIntegrity(b)
    }

    @Test
    fun cardOpFailedRestoreEditedRestoresPreviousCard() {
        val prev = card("c1")
        val edited = boardReducer(BoardModel(board()), EditCard(CardId("c1"), "T2", "D2", OpId("op1"), later), ann)
        val reverted = boardReducer(
            edited,
            CardOpFailed(OpId("op1"), CardId("c1"), "boom", InverseOp.RestoreEdited(prev)),
            ann,
        )
        val b = reverted.board!!
        assertEquals(prev, b.cards[CardId("c1")])
        assertIntegrity(b)
    }

    @Test
    fun cardOpFailedReAddDeletedReinsertsCardAtIndex() {
        val c1 = card("c1")
        val deleted = boardReducer(BoardModel(board()), DeleteCard(CardId("c1"), OpId("op1")), ann)
        val reverted = boardReducer(
            deleted,
            CardOpFailed(OpId("op1"), CardId("c1"), "boom", InverseOp.ReAddDeleted(c1, todo, 0)),
            ann,
        )
        val b = reverted.board!!
        assertEquals(c1, b.cards[CardId("c1")])
        assertEquals(0, b.column(todo).cardIds.indexOf(CardId("c1")))
        assertIntegrity(b)
    }

    @Test
    fun cardOpFailedReAddDeletedClampsIndex() {
        val c1 = card("c1")
        val deleted = boardReducer(BoardModel(board()), DeleteCard(CardId("c1"), OpId("op1")), ann)
        val reverted = boardReducer(
            deleted,
            CardOpFailed(OpId("op1"), CardId("c1"), "boom", InverseOp.ReAddDeleted(c1, todo, 99)),
            ann,
        )
        val b = reverted.board!!
        assertIntegrity(b)
        assertTrue(b.column(todo).cardIds.contains(CardId("c1")))
    }

    // --- (d) bot move/add ---

    @Test
    fun botMovedCardMutatesLikeMove() {
        val start = BoardModel(board())
        val next = boardReducer(start, BotMovedCard(CardId("c1"), done, 0), ann)
        val b = next.board!!
        assertFalse(b.column(todo).cardIds.contains(CardId("c1")))
        assertEquals(persistentListOf(CardId("c1")), b.column(done).cardIds)
        assertIntegrity(b)
    }

    @Test
    fun botAddedCardPutsCardAndAppendsId() {
        val botCard = card("cbot")
        val next = boardReducer(BoardModel(board()), BotAddedCard(done, botCard), ann)
        val b = next.board!!
        assertEquals(botCard, b.cards[CardId("cbot")])
        assertEquals(persistentListOf(CardId("cbot")), b.column(done).cardIds)
        assertIntegrity(b)
    }

    // --- (g) BoardRestored ---

    @Test
    fun boardRestoredReplacesBoardEvenFromNotLoaded() {
        val b = board()
        assertEquals(BoardModel(b), boardReducer(BoardModel(null), BoardRestored(b), ann))
    }

    @Test
    fun boardRestoredReplacesExistingBoard() {
        val replacement = board().copy(boardId = BoardId("b2"))
        val next = boardReducer(BoardModel(board()), BoardRestored(replacement), ann)
        assertEquals(BoardId("b2"), next.board!!.boardId)
    }

    // --- filterReducer ---

    @Test
    fun filterReducerSetsQuery() {
        assertEquals("hi", filterReducer(FilterModel(), SetFilterQuery("hi")).query)
    }

    @Test
    fun filterReducerSetsAssignee() {
        assertEquals(ann, filterReducer(FilterModel(), SetFilterAssignee(ann)).assignee)
    }

    @Test
    fun filterReducerTogglesLabelOnThenOff() {
        val l = LabelId("urgent")
        val on = filterReducer(FilterModel(), ToggleFilterLabel(l))
        assertTrue(on.labelIds.contains(l))
        val off = filterReducer(on, ToggleFilterLabel(l))
        assertFalse(off.labelIds.contains(l))
    }

    @Test
    fun filterReducerBoardClosedResets() {
        val start = FilterModel(query = "x", assignee = ann, labelIds = persistentSetOf(LabelId("l")))
        assertEquals(FilterModel(), filterReducer(start, BoardClosed))
    }

    @Test
    fun filterReducerReturnsSameInstanceForUnhandled() {
        val start = FilterModel()
        assertSame(start, filterReducer(start, Refresh))
    }

    // --- (h) syncReducer ---

    @Test
    fun syncAddsInFlightOnMoveRequested() {
        val next = syncReducer(SyncModel(), CardMoveRequested(CardId("c1"), todo, doing, 0, OpId("op1")))
        assertTrue(next.inFlight.contains(CardId("c1")))
    }

    @Test
    fun syncAddsInFlightOnAddEditDelete() {
        val a = syncReducer(SyncModel(), AddCard(todo, CardId("c1"), "t", "", OpId("o"), now))
        assertTrue(a.inFlight.contains(CardId("c1")))
        val e = syncReducer(SyncModel(), EditCard(CardId("c2"), "t", "", OpId("o"), now))
        assertTrue(e.inFlight.contains(CardId("c2")))
        val d = syncReducer(SyncModel(), DeleteCard(CardId("c3"), OpId("o")))
        assertTrue(d.inFlight.contains(CardId("c3")))
    }

    @Test
    fun syncRemovesInFlightOnOpSucceeded() {
        val start = SyncModel(inFlight = persistentSetOf(CardId("c1")))
        val next = syncReducer(start, CardOpSucceeded(OpId("op1"), CardId("c1")))
        assertFalse(next.inFlight.contains(CardId("c1")))
    }

    @Test
    fun syncRemovesInFlightOnOpFailed() {
        val start = SyncModel(inFlight = persistentSetOf(CardId("c1")))
        val next = syncReducer(
            start,
            CardOpFailed(OpId("op1"), CardId("c1"), "boom", InverseOp.DeleteAdded(CardId("c1"))),
        )
        assertFalse(next.inFlight.contains(CardId("c1")))
    }

    @Test
    fun syncStatusChangedFoldsFieldsButNotInFlight() {
        val start = SyncModel(inFlight = persistentSetOf(CardId("c1")))
        val next = syncReducer(
            start,
            SyncStatusChanged(
                online = false,
                pendingCount = 3,
                inFlight = persistentSetOf(CardId("zzz")),
                lastSyncedAt = later,
                lastError = "err",
            ),
        )
        assertFalse(next.online)
        assertEquals(3, next.pendingCount)
        assertEquals(later, next.lastSyncedAt)
        assertEquals("err", next.lastError)
        // inFlight is reducer-maintained, NOT overwritten from the action
        assertEquals(persistentSetOf(CardId("c1")), next.inFlight)
    }

    @Test
    fun syncBoardClosedResets() {
        val start = SyncModel(inFlight = persistentSetOf(CardId("c1")), pendingCount = 5, online = false)
        assertEquals(SyncModel(), syncReducer(start, BoardClosed))
    }

    @Test
    fun syncReducerReturnsSameInstanceForUnhandled() {
        val start = SyncModel()
        assertSame(start, syncReducer(start, Refresh))
    }

    // --- activityReducer ---

    @Test
    fun activityRecordAppendsEntry() {
        val entry = ActivityEntry("e1", ann, "did a thing", now)
        val next = activityReducer(ActivityModel(), RecordActivity(entry))
        assertEquals(persistentListOf(entry), next.entries)
    }

    @Test
    fun activityTrimsToLast50() {
        var model = ActivityModel()
        repeat(55) { i ->
            model = activityReducer(model, RecordActivity(ActivityEntry("e$i", ann, "s$i", now)))
        }
        assertEquals(50, model.entries.size)
        assertEquals("e5", model.entries.first().id)
        assertEquals("e54", model.entries.last().id)
    }

    @Test
    fun activityNotResetByBoardClosed() {
        val start = ActivityModel(persistentListOf(ActivityEntry("e1", ann, "s", now)))
        assertSame(start, activityReducer(start, BoardClosed))
    }

    // --- (f) undo helpers ---

    @Test
    fun pushUndoPushesPresentAndClearsFuture() {
        val b = board()
        val start = UndoModel(future = persistentListOf(board()))
        val next = pushUndo(start, b)
        assertEquals(persistentListOf(b), next.past)
        assertTrue(next.future.isEmpty())
    }

    @Test
    fun pushUndoCapDropsOldest() {
        var model = UndoModel(cap = 15)
        repeat(20) { model = pushUndo(model, board().copy(boardId = BoardId("b$it"))) }
        assertEquals(15, model.past.size)
        // oldest 5 dropped -> first is b5, last is b19
        assertEquals(BoardId("b5"), model.past.first().boardId)
        assertEquals(BoardId("b19"), model.past.last().boardId)
    }

    @Test
    fun undoRestoresPastAndPushesPresentToFuture() {
        val past0 = board().copy(boardId = BoardId("past"))
        val present = board().copy(boardId = BoardId("present"))
        val model = UndoModel(past = persistentListOf(past0))
        val result = undoReducer(model, Undo, present)
        assertEquals(past0, result.restored)
        assertTrue(result.model.past.isEmpty())
        assertEquals(persistentListOf(present), result.model.future)
    }

    @Test
    fun undoEmptyStackIsNoOp() {
        val model = UndoModel()
        val result = undoReducer(model, Undo, board())
        assertNull(result.restored)
        assertSame(model, result.model)
    }

    @Test
    fun undoWithNullPresentDoesNotPushFuture() {
        val past0 = board().copy(boardId = BoardId("past"))
        val model = UndoModel(past = persistentListOf(past0))
        val result = undoReducer(model, Undo, null)
        assertEquals(past0, result.restored)
        assertTrue(result.model.future.isEmpty())
    }

    @Test
    fun redoRestoresFutureAndPushesPresentToPast() {
        val future0 = board().copy(boardId = BoardId("future"))
        val present = board().copy(boardId = BoardId("present"))
        val model = UndoModel(future = persistentListOf(future0))
        val result = undoReducer(model, Redo, present)
        assertEquals(future0, result.restored)
        assertTrue(result.model.future.isEmpty())
        assertEquals(persistentListOf(present), result.model.past)
    }

    @Test
    fun redoEmptyStackIsNoOp() {
        val model = UndoModel()
        val result = undoReducer(model, Redo, board())
        assertNull(result.restored)
        assertSame(model, result.model)
    }

    @Test
    fun undoReducerBoardClosedResets() {
        val model = UndoModel(past = persistentListOf(board()), future = persistentListOf(board()))
        val result = undoReducer(model, BoardClosed, board())
        assertNull(result.restored)
        assertEquals(UndoModel(), result.model)
    }

    @Test
    fun undoReducerReturnsSameModelForUnhandled() {
        val model = UndoModel(past = persistentListOf(board()))
        val result = undoReducer(model, Refresh, board())
        assertNull(result.restored)
        assertSame(model, result.model)
    }
}
