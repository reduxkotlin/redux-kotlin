package org.reduxkotlin.bundle

import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.registry.StoreRegistry
import org.reduxkotlin.registry.TypedStoreRegistry
import org.reduxkotlin.registry.storeKey
import org.reduxkotlin.routing.RoutingBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RegistryExtensionsTest {
    private val block: RoutingBuilder.() -> Unit = {
        model(counterInitial()) { on<Increment> { s, a -> onIncrement(s, a) } }
    }

    @Test
    fun store_registry_get_or_create_is_cached_per_id() {
        val registry = StoreRegistry<String, ModelState>()
        val a1 = registry.getOrCreateConcurrentModelStore("a", block = block)
        val a2 = registry.getOrCreateConcurrentModelStore("a", block = block)
        val b = registry.getOrCreateConcurrentModelStore("b", block = block)
        assertSame(a1, a2)
        a1.dispatch(Increment(5))
        assertEquals(5, a1.state.get<CounterModel>().count)
        assertEquals(0, b.state.get<CounterModel>().count)
    }

    @Test
    fun typed_store_registry_get_or_create_works() {
        val registry = TypedStoreRegistry()
        val key = storeKey<String, ModelState>("counter")
        val s1 = registry.getOrCreateConcurrentModelStore(key, block = block)
        val s2 = registry.getOrCreateConcurrentModelStore(key, block = block)
        assertSame(s1, s2)
    }
}
