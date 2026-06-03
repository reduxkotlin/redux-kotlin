package org.reduxkotlin.sample.taskflow.feature.board

import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.core.AddCard
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.CardOpFailed
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.InverseOp
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.core.Undoable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ActionsTest {
    private val now = Instant.fromEpochMilliseconds(0)

    @Test
    fun userCardMutationsAreUndoable() {
        assertTrue(AddCard(ColumnId("a"), CardId("c"), "t", "d", OpId("o"), now) is Undoable)
        assertTrue(CardMoveRequested(CardId("c"), ColumnId("a"), ColumnId("b"), 0, OpId("o")) is Undoable)
    }

    @Test
    fun botActionsAreNotUndoable() {
        assertFalse(BotMovedCard(CardId("c"), ColumnId("b"), 0) is Undoable)
    }

    @Test
    fun asyncResultsAreNotUndoable() {
        val inverse = InverseOp.MoveBack(CardId("c"), ColumnId("a"), 0)
        assertFalse(CardOpFailed(OpId("o"), CardId("c"), "e", inverse) is Undoable)
    }

    @Test
    fun navIsNotUndoable() {
        assertFalse(Navigate(Route.Profile) is Undoable)
    }
}
