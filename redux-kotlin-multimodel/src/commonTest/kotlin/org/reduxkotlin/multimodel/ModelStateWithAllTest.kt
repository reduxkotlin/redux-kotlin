package org.reduxkotlin.multimodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private data class A(val n: Int)
private data class B(val s: String)
private data class Unregistered(val x: Int)

class ModelStateWithAllTest {

    @Test
    fun withAll_replaces_multiple_models_in_one_call() {
        val state = ModelState.of(A(1), B("x"))
        val next = state.withAll(mapOf(A::class to A(2), B::class to B("y")))
        assertEquals(A(2), next.get<A>())
        assertEquals(B("y"), next.get<B>())
    }

    @Test
    fun withAll_with_empty_map_returns_same_instance() {
        val state = ModelState.of(A(1))
        assertSame(state, state.withAll(emptyMap()))
    }

    @Test
    fun withAll_rejects_unregistered_model_class() {
        val state = ModelState.of(A(1))
        assertFailsWith<IllegalStateException> {
            state.withAll(mapOf(Unregistered::class to Unregistered(0)))
        }
    }
}
