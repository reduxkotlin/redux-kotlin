package org.reduxkotlin.sample.taskflow.reducer

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.action.Back
import org.reduxkotlin.sample.taskflow.action.CloseCard
import org.reduxkotlin.sample.taskflow.action.EnterEditMode
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.OpenCard
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.account.EditProfile
import org.reduxkotlin.sample.taskflow.feature.account.SessionModel
import org.reduxkotlin.sample.taskflow.feature.account.sessionReducer
import org.reduxkotlin.sample.taskflow.feature.board.CancelCreateCard
import org.reduxkotlin.sample.taskflow.feature.board.Refresh
import org.reduxkotlin.sample.taskflow.feature.board.StartCreateCard
import org.reduxkotlin.sample.taskflow.feature.boardlist.BoardListModel
import org.reduxkotlin.sample.taskflow.feature.boardlist.CreateBoard
import org.reduxkotlin.sample.taskflow.feature.boardlist.DEFAULT_BOARD_COLOR
import org.reduxkotlin.sample.taskflow.feature.boardlist.LoadBoardListSucceeded
import org.reduxkotlin.sample.taskflow.feature.boardlist.boardListReducer
import org.reduxkotlin.sample.taskflow.feature.collaborators.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.feature.collaborators.collaboratorsReducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class AccountReducersTest {
    private val fixedNow = Instant.fromEpochMilliseconds(1_700_000_000_000)

    // --- navReducer ---

    private val b1 = BoardId("b1")
    private val c1 = CardId("c1")
    private val col1 = ColumnId("col1")

    /** Stack with a board open, a card detail above it, in view mode. */
    private fun viewingCard() = NavModel(
        persistentListOf(Route.BoardList, Route.Board(b1), Route.CardDetail(c1, Route.CardDetail.Mode.View)),
    )

    @Test
    fun navigateToProfileResetsStackAndClearsOverlays() {
        val start = viewingCard()
        val next = navReducer(start, Navigate(Route.Profile))
        assertEquals(persistentListOf<Route>(Route.Profile), next.stack)
        assertNull(next.openCardId)
        assertNull(next.composing)
    }

    @Test
    fun navigateToBoardPushesOntoBoardList() {
        val next = navReducer(NavModel(), Navigate(Route.Board(b1)))
        assertEquals(persistentListOf<Route>(Route.BoardList, Route.Board(b1)), next.stack)
    }

    @Test
    fun navigateToBoardListResetsStack() {
        val next = navReducer(viewingCard(), Navigate(Route.BoardList))
        assertEquals(persistentListOf<Route>(Route.BoardList), next.stack)
    }

    @Test
    fun navigateToCurrentTopLevelIsIdempotent() {
        val start = NavModel(persistentListOf(Route.BoardList, Route.Board(b1)))
        assertSame(start, navReducer(start, Navigate(Route.Board(b1))))
    }

    @Test
    fun openCardPushesCardDetailInViewMode() {
        val start = NavModel(persistentListOf(Route.BoardList, Route.Board(b1)))
        val next = navReducer(start, OpenCard(c1))
        assertEquals(Route.CardDetail(c1, Route.CardDetail.Mode.View), next.current)
        assertEquals(c1, next.openCardId)
        assertEquals(3, next.stack.size)
    }

    @Test
    fun openCardSameCardIsIdempotent() {
        val start = viewingCard()
        assertSame(start, navReducer(start, OpenCard(c1)))
    }

    @Test
    fun closeCardPopsCardDetail() {
        val next = navReducer(viewingCard(), CloseCard)
        assertEquals(Route.Board(b1), next.current)
        assertNull(next.openCardId)
    }

    @Test
    fun enterEditModeFlipsTopCardDetailToEdit() {
        val next = navReducer(viewingCard(), EnterEditMode)
        assertEquals(Route.CardDetail(c1, Route.CardDetail.Mode.Edit), next.current)
        // The flip is in place: the stack depth stays the same.
        assertEquals(3, next.stack.size)
    }

    @Test
    fun enterEditModeIsNoOpWhenNotOnCardDetail() {
        val start = NavModel(persistentListOf(Route.BoardList, Route.Board(b1)))
        assertSame(start, navReducer(start, EnterEditMode))
    }

    @Test
    fun backFromEditModeReturnsToViewModeWithoutPopping() {
        val editing = NavModel(
            persistentListOf(Route.BoardList, Route.Board(b1), Route.CardDetail(c1, Route.CardDetail.Mode.Edit)),
        )
        val next = navReducer(editing, Back)
        assertEquals(Route.CardDetail(c1, Route.CardDetail.Mode.View), next.current)
        assertEquals(3, next.stack.size)
    }

    @Test
    fun backFromCardDetailPopsToBoard() {
        val next = navReducer(viewingCard(), Back)
        assertEquals(Route.Board(b1), next.current)
        assertEquals(2, next.stack.size)
    }

    @Test
    fun backFromBoardReturnsToBoardList() {
        val start = NavModel(persistentListOf(Route.BoardList, Route.Board(b1)))
        val next = navReducer(start, Back)
        assertEquals(Route.BoardList, next.current)
    }

    @Test
    fun backAtRootIsNoOp() {
        val start = NavModel(persistentListOf(Route.BoardList))
        assertSame(start, navReducer(start, Back))
    }

    @Test
    fun startCreateCardPushesComposeCard() {
        val start = NavModel(persistentListOf(Route.BoardList, Route.Board(b1)))
        val next = navReducer(start, StartCreateCard(col1))
        assertEquals(Route.ComposeCard(col1), next.current)
        assertEquals(col1, next.composing)
    }

    @Test
    fun cancelCreateCardPopsComposeCard() {
        val start = NavModel(persistentListOf(Route.BoardList, Route.Board(b1), Route.ComposeCard(col1)))
        val next = navReducer(start, CancelCreateCard)
        assertEquals(Route.Board(b1), next.current)
        assertNull(next.composing)
    }

    @Test
    fun activeBoardIdIsLowestBoardInStack() {
        // Even with a card detail at the top, the board lifecycle stays active.
        assertEquals(b1, viewingCard().activeBoardId)
        // No board on the stack -> null.
        assertNull(NavModel().activeBoardId)
    }

    @Test
    fun isEmptyStackEdgeCases() {
        val root = NavModel()
        assertTrue(root.stack.size == 1)
        assertFalse(root.stack.isEmpty())
    }

    @Test
    fun navReducerReturnsSameInstanceForUnhandled() {
        val start = NavModel()
        assertSame(start, navReducer(start, Refresh))
    }

    // --- sessionReducer ---

    @Test
    fun editProfileSetsBioAndKeepsAccountId() {
        val start = SessionModel(AccountId("ann"), bio = "old")
        val next = sessionReducer(start, EditProfile("Annie", "annie@x", "av", "new bio"))
        assertEquals("new bio", next.bio)
        assertEquals(AccountId("ann"), next.accountId)
    }

    @Test
    fun editProfileNullBioClearsBio() {
        val start = SessionModel(AccountId("ann"), bio = "old")
        val next = sessionReducer(start, EditProfile("Annie", "annie@x", "av", null))
        assertNull(next.bio)
    }

    @Test
    fun sessionReducerReturnsSameInstanceForUnhandled() {
        val start = SessionModel(AccountId("ann"))
        assertSame(start, sessionReducer(start, Refresh))
    }

    // --- boardListReducer ---

    @Test
    fun loadBoardListSucceededKeysByIdAndOrders() {
        val a = summary("a", "Alpha")
        val b = summary("b", "Beta")
        val next = boardListReducer(
            BoardListModel(),
            LoadBoardListSucceeded(persistentListOf(a, b)),
        )
        assertEquals(a, next.boards[BoardId("a")])
        assertEquals(b, next.boards[BoardId("b")])
        assertEquals(persistentListOf(BoardId("a"), BoardId("b")), next.order)
    }

    @Test
    fun loadBoardListSucceededUsesCountsAsIs() {
        val a = summary("a", "Alpha", cardCount = 7, doneCount = 3)
        val next = boardListReducer(BoardListModel(), LoadBoardListSucceeded(persistentListOf(a)))
        val loaded = next.boards[BoardId("a")]!!
        assertEquals(7, loaded.cardCount)
        assertEquals(3, loaded.doneCount)
    }

    @Test
    fun createBoardAddsSummaryWithDefaultsAndAppendsOrder() {
        val existing = summary("a", "Alpha")
        val start = BoardListModel(
            boards = persistentMapOf(existing.id to existing),
            order = persistentListOf(existing.id),
        )
        val next = boardListReducer(
            start,
            CreateBoard(BoardId("b"), "Beta", fixedNow),
        )
        val created = next.boards[BoardId("b")]!!
        assertEquals(BoardId("b"), created.id)
        assertEquals("Beta", created.name)
        assertEquals(DEFAULT_BOARD_COLOR, created.color)
        assertEquals(0, created.cardCount)
        assertEquals(0, created.doneCount)
        assertEquals(fixedNow, created.updatedAt)
        assertEquals(persistentListOf(BoardId("a"), BoardId("b")), next.order)
    }

    @Test
    fun boardListReducerReturnsSameInstanceForUnhandled() {
        val start = BoardListModel()
        assertSame(start, boardListReducer(start, Refresh))
    }

    // --- collaboratorsReducer ---

    @Test
    fun editProfileUpdatesExistingSelfCollaborator() {
        val selfId = AccountId("ann")
        val self = AccountSummary(selfId, "Ann", "ann@x", "av")
        val start = CollaboratorsModel(persistentMapOf(selfId to self))
        val next = collaboratorsReducer(
            start,
            EditProfile("Annie", "annie@x", "av2", "bio"),
            selfId,
        )
        val updated = next.byId[selfId]!!
        assertEquals("Annie", updated.displayName)
        assertEquals("annie@x", updated.email)
        assertEquals("av2", updated.avatarUrl)
    }

    @Test
    fun editProfileInsertsSelfCollaboratorWhenAbsent() {
        val selfId = AccountId("ann")
        val start = CollaboratorsModel()
        val next = collaboratorsReducer(
            start,
            EditProfile("Annie", "annie@x", "av2", "bio"),
            selfId,
        )
        val inserted = next.byId[selfId]!!
        assertEquals(selfId, inserted.id)
        assertEquals("Annie", inserted.displayName)
        assertEquals("annie@x", inserted.email)
        assertEquals("av2", inserted.avatarUrl)
    }

    @Test
    fun collaboratorsReducerReturnsSameInstanceForUnhandled() {
        val selfId = AccountId("ann")
        val self = AccountSummary(selfId, "A", "e", "a")
        val start = CollaboratorsModel(persistentMapOf(selfId to self))
        assertSame(start, collaboratorsReducer(start, Refresh, selfId))
    }

    private fun summary(id: String, name: String, cardCount: Int = 0, doneCount: Int = 0) =
        BoardSummary(BoardId(id), name, DEFAULT_BOARD_COLOR, cardCount, doneCount, fixedNow)
}
