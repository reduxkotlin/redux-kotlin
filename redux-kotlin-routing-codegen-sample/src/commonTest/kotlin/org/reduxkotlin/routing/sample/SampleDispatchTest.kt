package org.reduxkotlin.routing.sample

import org.reduxkotlin.routing.createModelStore
import org.reduxkotlin.routing.install
import org.reduxkotlin.routing.sample.generated.SampleModule
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleDispatchTest {
    @Test
    fun generated_module_dispatches() {
        val store = createModelStore { install(SampleModule) }
        store.dispatch(Increment(3))
        store.dispatch(Increment(4))
        assertEquals(7, store.state.get<CounterModel>().count)
        store.dispatch(Reset)
        assertEquals(0, store.state.get<CounterModel>().count)
    }
}
