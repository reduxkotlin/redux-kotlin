package org.reduxkotlin.sample.taskflow.reducer

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.action.CancelCreateCard
import org.reduxkotlin.sample.taskflow.action.CloseCard
import org.reduxkotlin.sample.taskflow.action.CreateBoard
import org.reduxkotlin.sample.taskflow.action.EditProfile
import org.reduxkotlin.sample.taskflow.action.LoadBoardListSucceeded
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.OpenCard
import org.reduxkotlin.sample.taskflow.action.Refresh
import org.reduxkotlin.sample.taskflow.action.StartCreateCard
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.BoardListModel
import org.reduxkotlin.sample.taskflow.model.BoardSummary
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.NavModel
import org.reduxkotlin.sample.taskflow.model.Route
import org.reduxkotlin.sample.taskflow.model.SessionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

class AccountReducersTest {
    private val fixedNow = Instant.fromEpochMilliseconds(1_700_000_000_000)

    // --- navReducer ---

    @Test
    fun navigateSetsRouteAndClearsCardAndComposing() {
        val start = NavModel(
            route = Route.BoardList,
            openCardId = CardId("c1"),
            composing = ColumnId("col1"),
        )
        val next = navReducer(start, Navigate(Route.Profile))
        assertEquals(Route.Profile, next.route)
        assertNull(next.openCardId)
        assertNull(next.composing)
    }

    @Test
    fun openCardSetsOpenCardId() {
        val next = navReducer(NavModel(), OpenCard(CardId("c1")))
        assertEquals(CardId("c1"), next.openCardId)
    }

    @Test
    fun closeCardClearsOpenCardId() {
        val start = NavModel(openCardId = CardId("c1"))
        val next = navReducer(start, CloseCard)
        assertNull(next.openCardId)
    }

    @Test
    fun startCreateCardSetsComposing() {
        val next = navReducer(NavModel(), StartCreateCard(ColumnId("col1")))
        assertEquals(ColumnId("col1"), next.composing)
    }

    @Test
    fun cancelCreateCardClearsComposing() {
        val start = NavModel(composing = ColumnId("col1"))
        val next = navReducer(start, CancelCreateCard)
        assertNull(next.composing)
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
