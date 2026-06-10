package org.reduxkotlin.sample.taskflow.app.persistence

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.LabelId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.board.FilterModel
import kotlin.test.Test
import kotlin.test.assertEquals

class UiSnapshotTest {

    @Test
    fun fullStackAndFilterRoundTripThroughJson() {
        val nav = NavModel(
            persistentListOf(
                Route.BoardList,
                Route.Board(BoardId("b1")),
                Route.CardDetail(CardId("c9"), Route.CardDetail.Mode.Edit),
            ),
        )
        val filter = FilterModel(
            query = "urgent",
            assignee = AccountId("a3"),
            labelIds = persistentSetOf(LabelId("l1"), LabelId("l2")),
        )

        val json = encodeUiSnapshot(nav, filter)
        val restored = decodeUiSnapshot(json)

        assertEquals(nav, restored.nav)
        assertEquals(filter, restored.filter)
    }

    @Test
    fun composeCardAndDefaultsRoundTrip() {
        val nav = NavModel(persistentListOf(Route.Settings, Route.ComposeCard(ColumnId("col2"))))
        val filter = FilterModel()

        val restored = decodeUiSnapshot(encodeUiSnapshot(nav, filter))

        assertEquals(nav, restored.nav)
        assertEquals(filter, restored.filter)
    }
}
