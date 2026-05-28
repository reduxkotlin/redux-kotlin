package org.reduxkotlin.routing

import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private data class Counter(val n: Int)
private data class Flag(val on: Boolean)
private object Inc
private object Toggle
private object Unhandled

class RoutedReducerTest {

    private fun table(): Map<Any, List<Cell>> = mapOf(
        Inc::class to listOf(
            Cell("counter") { working, _ ->
                val c = working.get(Counter::class)
                mapOf(Counter::class to c.copy(n = c.n + 1))
            },
        ),
    )

    @Test
    fun dispatch_runs_only_the_matching_cell() {
        val reducer = routedReducer(table(), broadcasts = emptyList(), devChecks = false, onWrite = null)
        val s0 = ModelState.of(Counter(0), Flag(false))
        val s1 = reducer(s0, Inc)
        assertEquals(1, s1.get<Counter>().n)
        assertEquals(false, s1.get<Flag>().on)
    }

    @Test
    fun unhandled_action_returns_same_instance() {
        val reducer = routedReducer(table(), broadcasts = emptyList(), devChecks = false, onWrite = null)
        val s0 = ModelState.of(Counter(0), Flag(false))
        assertSame(s0, reducer(s0, Unhandled))
    }

    @Test
    fun handled_action_with_no_change_returns_same_instance() {
        val noop = mapOf<Any, List<Cell>>(
            Toggle::class to listOf(Cell("flag") { _, _ -> emptyMap() }),
        )
        val reducer = routedReducer(noop, broadcasts = emptyList(), devChecks = false, onWrite = null)
        val s0 = ModelState.of(Flag(false))
        assertSame(s0, reducer(s0, Toggle))
    }
}
