package org.reduxkotlin.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RegistryCoreTest {

    private fun coreOfStrings() = RegistryCore<String, String>()

    @Test
    fun get_returns_null_when_absent() {
        val core = coreOfStrings()
        assertNull(core.get("missing"))
        assertEquals(0, core.size)
        assertTrue(core.isEmpty)
    }

    @Test
    fun getOrCreate_invokes_creator_once_for_first_call_and_inserts() {
        val core = coreOfStrings()
        var calls = 0
        val v = core.getOrCreate("k") {
            calls++
            "value-1"
        }
        assertEquals("value-1", v)
        assertEquals(1, calls)
        assertEquals("value-1", core.get("k"))
        assertEquals(1, core.size)
        assertFalse(core.isEmpty)
    }

    @Test
    fun getOrCreate_returns_existing_without_invoking_creator() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "value-1" }
        var calls = 0
        val v = core.getOrCreate("k") {
            calls++
            "value-2"
        }
        assertEquals("value-1", v)
        assertEquals(0, calls)
    }

    @Test
    fun remove_returns_true_when_present_and_false_when_absent() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "v" }
        assertTrue(core.remove("k"))
        assertFalse(core.remove("k"))
        assertNull(core.get("k"))
    }

    @Test
    fun clear_empties_all_entries() {
        val core = coreOfStrings()
        repeat(5) { i -> core.getOrCreate("k$i") { "v$i" } }
        assertEquals(5, core.size)
        core.clear()
        assertEquals(0, core.size)
        assertTrue(core.isEmpty)
        assertNull(core.get("k0"))
    }

    @Test
    fun getOrCreate_after_remove_recreates_and_re_invokes_creator() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "first" }
        core.remove("k")
        var calls = 0
        val v = core.getOrCreate("k") {
            calls++
            "second"
        }
        assertEquals("second", v)
        assertEquals(1, calls)
    }

    @Test
    fun creator_throwing_leaves_registry_unmodified() {
        val core = coreOfStrings()
        val boom = RuntimeException("nope")
        val thrown = try {
            core.getOrCreate("k") { throw boom }
            null
        } catch (t: Throwable) {
            t
        }
        assertSame(boom, thrown)
        assertNull(core.get("k"))
        assertEquals(0, core.size)
    }

    @Test
    fun added_event_fires_on_creation_only_not_on_hit() {
        val core = coreOfStrings()
        val events = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { events += it }

        core.getOrCreate("k") { "v" }
        core.getOrCreate("k") { "v2" }

        assertEquals(listOf<RegistryCore.Event<String>>(RegistryCore.Event.Added("k")), events)
    }

    @Test
    fun removed_event_fires_only_when_remove_returns_true() {
        val core = coreOfStrings()
        core.getOrCreate("k") { "v" }
        val events = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { events += it }

        core.remove("k")
        core.remove("k") // absent — no event

        assertEquals(listOf<RegistryCore.Event<String>>(RegistryCore.Event.Removed("k")), events)
    }

    @Test
    fun clear_fires_one_removed_event_per_evicted_entry() {
        val core = coreOfStrings()
        repeat(3) { i -> core.getOrCreate("k$i") { "v$i" } }
        val events = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { events += it }

        core.clear()

        assertEquals(3, events.size)
        assertEquals(setOf("k0", "k1", "k2"), events.map { it.id }.toSet())
        events.forEach { assertTrue(it is RegistryCore.Event.Removed) }
    }

    @Test
    fun unsubscribe_stops_events() {
        val core = coreOfStrings()
        val events = mutableListOf<RegistryCore.Event<String>>()
        val unsubscribe = core.addListener { events += it }

        core.getOrCreate("k1") { "v" }
        unsubscribe()
        core.getOrCreate("k2") { "v" }

        assertEquals(listOf<RegistryCore.Event<String>>(RegistryCore.Event.Added("k1")), events)
    }

    @Test
    fun multiple_listeners_each_receive_each_event() {
        val core = coreOfStrings()
        val a = mutableListOf<RegistryCore.Event<String>>()
        val b = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { a += it }
        core.addListener { b += it }

        core.getOrCreate("k") { "v" }
        core.remove("k")

        val expected = listOf(
            RegistryCore.Event.Added("k"),
            RegistryCore.Event.Removed("k"),
        )
        assertEquals(expected, a)
        assertEquals(expected, b)
    }

    @Test
    fun listener_can_read_registry_state_from_callback() {
        val core = coreOfStrings()
        var observedSize = -1
        var observedValue: String? = null
        core.addListener {
            observedSize = core.size
            observedValue = core.get("k")
        }

        core.getOrCreate("k") { "v" }

        assertEquals(1, observedSize)
        assertEquals("v", observedValue)
    }

    @Test
    fun listener_throwing_propagates_and_skips_remaining_listeners() {
        val core = coreOfStrings()
        val captured = mutableListOf<RegistryCore.Event<String>>()
        core.addListener { throw IllegalStateException("boom") }
        core.addListener { captured += it } // would be invoked second; should be skipped

        val thrown = try {
            core.getOrCreate("k") { "v" }
            null
        } catch (t: Throwable) {
            t
        }

        assertTrue(thrown is IllegalStateException)
        assertEquals(0, captured.size)
        // Mutation already happened before the throw.
        assertEquals("v", core.get("k"))
    }
}
