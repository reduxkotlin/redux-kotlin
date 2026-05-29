package org.reduxkotlin.sample.taskflow.action

import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.OpId
import org.reduxkotlin.sample.taskflow.model.Route
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
