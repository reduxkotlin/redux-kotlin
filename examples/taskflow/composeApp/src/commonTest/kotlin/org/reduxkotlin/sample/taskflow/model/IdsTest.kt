package org.reduxkotlin.sample.taskflow.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdsTest {

    @Test
    fun accountId_valueIdentity() {
        assertEquals(AccountId("a"), AccountId("a"))
        assertNotEquals(AccountId("a"), AccountId("b"))
    }

    @Test
    fun boardId_valueIdentity() {
        assertEquals(BoardId("a"), BoardId("a"))
        assertNotEquals(BoardId("a"), BoardId("b"))
    }

    @Test
    fun columnId_valueIdentity() {
        assertEquals(ColumnId("a"), ColumnId("a"))
        assertNotEquals(ColumnId("a"), ColumnId("b"))
    }

    @Test
    fun cardId_valueIdentity() {
        assertEquals(CardId("a"), CardId("a"))
        assertNotEquals(CardId("a"), CardId("b"))
    }

    @Test
    fun labelId_valueIdentity() {
        assertEquals(LabelId("a"), LabelId("a"))
        assertNotEquals(LabelId("a"), LabelId("b"))
    }

    @Test
    fun opId_valueIdentity() {
        assertEquals(OpId("a"), OpId("a"))
        assertNotEquals(OpId("a"), OpId("b"))
    }
}
