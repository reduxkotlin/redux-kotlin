package org.reduxkotlin.bundle

import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import org.reduxkotlin.granular.subscribeTo
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.multimodel.granular.subscribeToModel
import org.reduxkotlin.registry.StoreRegistry
import org.reduxkotlin.routing.RoutingBuilder
import org.reduxkotlin.routing.createModelStore
import org.reduxkotlin.threadsafe.ThreadSafeStore
import org.reduxkotlin.threadsafe.createThreadSafeStore
import kotlin.test.Test
import kotlin.test.assertTrue

/** Proves every bundled module's public API is reachable through the single bundle dependency. */
class SurfaceReachableTest {
    @Test
    fun every_bundled_module_symbol_is_reachable() {
        // KClass references prove the type symbols are accessible from each module.
        val types = listOf(
            Store::class, // redux-kotlin core
            ThreadSafeStore::class, // redux-kotlin-threadsafe
            ModelState::class, // redux-kotlin-multimodel
            StoreRegistry::class, // redux-kotlin-registry
            RoutingBuilder::class, // redux-kotlin-routing
        )
        // Typed lambdas reference the factory functions so imports resolve + re-exports are proven.
        val fns = listOf<Any>(
            { r: Reducer<Int>, s: Int -> createStore(r, s) },
            { r: Reducer<Int>, s: Int -> createThreadSafeStore(r, s) },
            { b: RoutingBuilder.() -> Unit -> createModelStore(block = b) },
        )
        // Extension function imports for granular modules: resolved via typed lambdas.
        val granular = listOf<Any>(
            { s: Store<Int> -> s.subscribeTo({ it }, triggerOnSubscribe = false) { _, _ -> } },
            { s: Store<ModelState> -> s.subscribeToModel(CounterModel::class, { it.count }) { _, _ -> } },
        )
        assertTrue(types.isNotEmpty() && fns.isNotEmpty() && granular.isNotEmpty())
    }
}
