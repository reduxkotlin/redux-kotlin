package org.reduxkotlin.bundle

import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals

private data class BundleCounterModel(val n: Int = 0)
private data class BundleInc(val by: Int)

class CreateConcurrentModelStorePreloadTest {
    @Test
    fun preloadedStateSeedsConcurrentStore() {
        val store = createConcurrentModelStore(
            preloadedState = ModelState.of(BundleCounterModel(7)),
        ) {
            model(BundleCounterModel()) { on<BundleInc> { m, a -> m.copy(n = m.n + a.by) } }
        }
        assertEquals(7, store.state.get<BundleCounterModel>().n)
    }
}
