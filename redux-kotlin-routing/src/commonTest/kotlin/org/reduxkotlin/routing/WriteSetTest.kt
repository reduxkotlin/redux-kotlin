package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals

private data class Foo(val n: Int)
private data class Bar(val s: String)

class WriteSetTest {

    @Test
    fun writeSet_collects_models_keyed_by_class() {
        val ws = writeSet {
            set(Foo(1))
            set(Bar("x"))
        }
        assertEquals(2, ws.changes.size)
        assertEquals(Foo(1), ws.changes[Foo::class])
        assertEquals(Bar("x"), ws.changes[Bar::class])
    }

    @Test
    fun writeSet_last_set_for_a_class_wins() {
        val ws = writeSet {
            set(Foo(1))
            set(Foo(2))
        }
        assertEquals(1, ws.changes.size)
        assertEquals(Foo(2), ws.changes[Foo::class])
    }
}
