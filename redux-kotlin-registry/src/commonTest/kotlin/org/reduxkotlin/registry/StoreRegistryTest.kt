package org.reduxkotlin.registry

import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoreRegistryTest {

    private data class CounterState(val n: Int = 0)
    private object Inc

    private val reducer: (CounterState, Any) -> CounterState = { s, a ->
        if (a is Inc) s.copy(n = s.n + 1) else s
    }

    private fun newRegistry() = StoreRegistry<String, CounterState>()

    private fun newStore(initial: Int = 0): Store<CounterState> = createStore(reducer, CounterState(initial))

    @Test
    fun get_returns_null_when_absent() {
        assertNull(newRegistry().get("missing"))
    }

    @Test
    fun getOrCreate_returns_same_store_on_repeated_calls_and_runs_creator_once() {
        val reg = newRegistry()
        var calls = 0
        val s1 = reg.getOrCreate("k") {
            calls++
            newStore()
        }
        val s2 = reg.getOrCreate("k") {
            calls++
            newStore()
        }
        assertSame(s1, s2)
        assertEquals(1, calls)
    }

    @Test
    fun remove_returns_true_when_present_and_false_when_absent() {
        val reg = newRegistry()
        reg.getOrCreate("k") { newStore() }
        assertTrue(reg.remove("k"))
        assertFalse(reg.remove("k"))
        assertNull(reg.get("k"))
    }

    @Test
    fun clear_empties_registry() {
        val reg = newRegistry()
        repeat(3) { i -> reg.getOrCreate("k$i") { newStore(i) } }
        assertEquals(3, reg.size)
        reg.clear()
        assertTrue(reg.isEmpty)
        assertEquals(0, reg.size)
    }

    @Test
    fun added_then_removed_events_fire_in_order() {
        val reg = newRegistry()
        val events = mutableListOf<RegistryEvent<String>>()
        reg.addListener { events += it }

        reg.getOrCreate("k") { newStore() }
        reg.remove("k")

        assertEquals(
            listOf(
                RegistryEvent.Added("k"),
                RegistryEvent.Removed("k"),
            ),
            events,
        )
    }

    @Test
    fun clear_fires_one_removed_event_per_entry() {
        val reg = newRegistry()
        repeat(2) { i -> reg.getOrCreate("k$i") { newStore() } }
        val events = mutableListOf<RegistryEvent<String>>()
        reg.addListener { events += it }

        reg.clear()

        assertEquals(2, events.size)
        events.forEach { assertTrue(it is RegistryEvent.Removed) }
    }

    @Test
    fun unsubscribe_stops_events() {
        val reg = newRegistry()
        val events = mutableListOf<RegistryEvent<String>>()
        val off = reg.addListener { events += it }

        reg.getOrCreate("a") { newStore() }
        off()
        reg.getOrCreate("b") { newStore() }

        assertEquals(1, events.size)
    }

    @Test
    fun returned_store_remains_usable_after_remove() {
        val reg = newRegistry()
        val store = reg.getOrCreate("k") { newStore() }
        reg.remove("k")
        // Caller still holds the ref; dispatch still works.
        store.dispatch(Inc)
        assertEquals(1, store.getState().n)
    }
}
