package org.reduxkotlin.sample.taskflow.app.persistence

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiSnapshotSaverTest {
    @Test
    fun saverRoundTripsNavAndFilterThroughJson() {
        val nav = NavModel(
            persistentListOf(Route.BoardList, Route.Board(BoardId("b1")), Route.CardDetail(CardId("c9"))),
        )
        val filter = FilterModel(
            query = "urgent",
            assignee = AccountId("a3"),
            labelIds = persistentSetOf(LabelId("l1")),
        )
        val ms = ModelState.of(nav, filter)
        val snap = accountUiSaver.save(ms)
        val json = accountUiSaver.json.encodeToString(accountUiSaver.serializer, snap)
        val decoded = accountUiSaver.json.decodeFromString(accountUiSaver.serializer, json)
        val action = accountUiSaver.restore(decoded) as RestoreUiState
        assertEquals(BoardId("b1"), action.nav.activeBoardId)
        assertEquals("urgent", action.filter.query)
        assertEquals(AccountId("a3"), action.filter.assignee)
    }

    @Test
    fun cardDetailRestoresInViewMode() {
        val nav = NavModel(
            persistentListOf(
                Route.BoardList,
                Route.Board(BoardId("b1")),
                Route.CardDetail(CardId("c9"), Route.CardDetail.Mode.Edit),
            ),
        )
        val action = accountUiSaver.restore(accountUiSaver.save(ModelState.of(nav, FilterModel()))) as RestoreUiState
        val top = action.nav.stack.last()
        assertTrue(top is Route.CardDetail && top.mode == Route.CardDetail.Mode.View)
    }
}
