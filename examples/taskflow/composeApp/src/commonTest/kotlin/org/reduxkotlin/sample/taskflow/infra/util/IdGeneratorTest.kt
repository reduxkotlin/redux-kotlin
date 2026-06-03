package org.reduxkotlin.sample.taskflow.infra.util

import org.reduxkotlin.sample.taskflow.infra.util.DefaultIdGenerator
import org.reduxkotlin.sample.taskflow.infra.util.FakeIdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdGeneratorTest {
    @Test
    fun fakeGeneratorReturnsDeterministicSequentialIds() {
        val gen = FakeIdGenerator()
        assertEquals("op-1", gen.newOpId().v)
        assertEquals("card-2", gen.newCardId().v)
        assertEquals("board-3", gen.newBoardId().v)
        assertEquals("column-4", gen.newColumnId().v)
        assertEquals("activity-5", gen.newActivityId())
    }

    @Test
    fun fakeGeneratorHonorsStartOffset() {
        val gen = FakeIdGenerator(start = 10)
        assertEquals("op-11", gen.newOpId().v)
        assertEquals("op-12", gen.newOpId().v)
    }

    @Test
    fun fakeGeneratorReturnsDistinctIdsPerKind() {
        val gen = FakeIdGenerator()
        val cards = List(100) { gen.newCardId().v }
        assertEquals(cards.size, cards.toSet().size)
    }

    @Test
    fun defaultGeneratorReturnsDistinctIds() {
        var n = 0
        val gen = DefaultIdGenerator(uuid = { "uuid-${++n}" })
        val op = gen.newOpId().v
        val card = gen.newCardId().v
        val board = gen.newBoardId().v
        val column = gen.newColumnId().v
        val activity = gen.newActivityId()
        val all = listOf(op, card, board, column, activity)
        assertEquals(all.size, all.toSet().size)
        assertNotEquals(op, card)
    }

    @Test
    fun defaultGeneratorWithRealUuidReturnsDistinctIds() {
        val gen = DefaultIdGenerator()
        val ids = List(50) { gen.newCardId().v }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }
}
