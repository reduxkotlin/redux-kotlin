package org.reduxkotlin.routing

import org.reduxkotlin.multimodel.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals

private data class CounterModel(val n: Int = 0)
private data class LabelModel(val text: String = "default")
private data class PreloadInc(val by: Int)

class CreateModelStorePreloadTest {
    @Test
    fun preloadedStateOverridesDeclaredDefaults() {
        val store = createModelStore(
            preloadedState = ModelState.of(CounterModel(42)),
        ) {
            model(CounterModel()) { on<PreloadInc> { m, a -> m.copy(n = m.n + a.by) } }
            model(LabelModel()) { }
        }
        assertEquals(42, store.state.get<CounterModel>().n)
        assertEquals("default", store.state.get<LabelModel>().text)
    }

    @Test
    fun nullPreloadedStateUsesDeclaredDefaults() {
        val store = createModelStore {
            model(CounterModel()) { on<PreloadInc> { m, a -> m.copy(n = m.n + a.by) } }
        }
        assertEquals(0, store.state.get<CounterModel>().n)
    }
}
