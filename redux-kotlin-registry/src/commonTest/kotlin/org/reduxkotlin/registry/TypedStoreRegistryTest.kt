package org.reduxkotlin.registry

import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TypedStoreRegistryTest {

    private data class StateA(val a: Int = 0)
    private data class StateB(val b: String = "")

    private val noopReducerA: (StateA, Any) -> StateA = { s, _ -> s }
    private val noopReducerB: (StateB, Any) -> StateB = { s, _ -> s }

    private fun storeA() = createStore(noopReducerA, StateA())
    private fun storeB() = createStore(noopReducerB, StateB())

    @Test
    fun two_storeKeys_with_same_id_and_type_are_equal() {
        val k1 = storeKey<String, StateA>("x")
        val k2 = storeKey<String, StateA>("x")
        assertEquals(k1, k2)
        assertEquals(k1.hashCode(), k2.hashCode())
    }

    @Test
    fun two_storeKeys_with_same_id_but_different_state_types_are_distinct() {
        val k1 = storeKey<String, StateA>("x")
        val k2 = storeKey<String, StateB>("x")
        assertNotEquals<StoreKey<*, *>>(k1, k2)
    }

    @Test
    fun storeKey_with_int_id_is_supported() {
        val k = storeKey<Int, StateA>(42)
        assertEquals(42, k.id)
        assertEquals(StateA::class, k.stateType)
    }

    @Test
    fun getOrCreate_returns_typed_store_without_explicit_cast() {
        val reg = TypedStoreRegistry()
        val store: Store<StateA> = reg.getOrCreate(storeKey<String, StateA>("x")) { storeA() }
        // Calling the typed API without casting compiles — that's the test.
        assertEquals(StateA(), store.getState())
    }

    @Test
    fun heterogeneous_stores_under_same_id_string_are_distinct_entries() {
        val reg = TypedStoreRegistry()
        val a: Store<StateA> = reg.getOrCreate(storeKey<String, StateA>("x")) { storeA() }
        val b: Store<StateB> = reg.getOrCreate(storeKey<String, StateB>("x")) { storeB() }

        assertEquals(2, reg.size)
        assertSame(a, reg.get(storeKey<String, StateA>("x")))
        assertSame(b, reg.get(storeKey<String, StateB>("x")))
    }

    @Test
    fun creator_invoked_at_most_once_per_key() {
        val reg = TypedStoreRegistry()
        var calls = 0
        val k = storeKey<String, StateA>("x")
        reg.getOrCreate(k) {
            calls++
            storeA()
        }
        reg.getOrCreate(k) {
            calls++
            storeA()
        }
        assertEquals(1, calls)
    }

    @Test
    fun remove_returns_true_then_false_and_get_returns_null() {
        val reg = TypedStoreRegistry()
        val k = storeKey<String, StateA>("x")
        reg.getOrCreate(k) { storeA() }
        assertTrue(reg.remove(k))
        assertEquals(0, reg.size)
        assertNull(reg.get(k))
        assertEquals(false, reg.remove(k))
    }

    @Test
    fun clear_empties_and_fires_per_entry_events() {
        val reg = TypedStoreRegistry()
        reg.getOrCreate(storeKey<String, StateA>("a")) { storeA() }
        reg.getOrCreate(storeKey<String, StateB>("b")) { storeB() }
        val events = mutableListOf<TypedRegistryEvent>()
        reg.addListener { events += it }

        reg.clear()

        assertEquals(2, events.size)
        events.forEach { assertTrue(it is TypedRegistryEvent.Removed) }
        assertEquals(0, reg.size)
    }

    @Test
    fun added_event_carries_concrete_key_for_narrowing() {
        val reg = TypedStoreRegistry()
        val events = mutableListOf<TypedRegistryEvent>()
        reg.addListener { events += it }

        val k = storeKey<String, StateA>("x")
        reg.getOrCreate(k) { storeA() }

        assertEquals(1, events.size)
        val e = events.single()
        assertTrue(e is TypedRegistryEvent.Added)
        assertEquals(k, e.key)
        // Consumers can narrow by stateType:
        assertEquals(StateA::class, e.key.stateType)
    }
}
