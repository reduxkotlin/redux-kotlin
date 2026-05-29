package org.reduxkotlin.bundle

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreFactoryTest {
    private fun store() = createConcurrentModelStore {
        model(counterInitial()) {
            on<Increment> { s, a -> onIncrement(s, a) }
            on<Reset> { s, a -> onReset(s, a) }
        }
    }

    @Test
    fun concurrent_model_store_dispatches() {
        val s = store()
        s.dispatch(Increment(3))
        s.dispatch(Increment(4))
        assertEquals(7, s.state.get<CounterModel>().count)
        s.dispatch(Reset)
        assertEquals(0, s.state.get<CounterModel>().count)
    }
}
