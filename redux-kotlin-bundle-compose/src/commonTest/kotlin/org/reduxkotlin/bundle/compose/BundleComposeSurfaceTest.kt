package org.reduxkotlin.bundle.compose

import org.reduxkotlin.bundle.createConcurrentModelStore
import org.reduxkotlin.compose.StableStore
import kotlin.test.Test
import kotlin.test.assertTrue

/** Proves the base bundle AND Compose bindings are reachable through this single dependency. */
class BundleComposeSurfaceTest {
    @Test
    fun base_bundle_and_compose_bindings_are_reachable() {
        // reference both so the imports are used; adapt the compose reference to the symbol you imported
        val a: Any = ::createConcurrentModelStore
        val b = StableStore::class
        assertTrue(a != Unit)
        assertTrue(b.simpleName == "StableStore")
    }
}
