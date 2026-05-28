package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore
import org.reduxkotlin.middleware
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateConcurrentStoreTest {

    private data class S(val count: Int = 0)
    private object Inc
    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    @Test
    fun factory_creates_a_working_store_with_init_state_seeded() {
        val s = createConcurrentStore(reducer, S(count = 5))
        assertEquals(5, s.state.count)
        s.dispatch(Inc)
        assertEquals(6, s.state.count)
    }

    @Test
    fun typed_factory_dispatches_typed_actions() {
        val s = createTypedConcurrentStore<S, Inc>({ st, _ -> st.copy(count = st.count + 1) }, S())
        s.dispatch(Inc)
        assertEquals(1, s.state.count)
    }

    @Test
    fun middleware_redispatch_through_enhancer_is_routed() {
        val doubling = middleware<S> { store, next, action ->
            val result = next(action)
            if (action is Inc && store.state.count == 1) {
                store.dispatch(Inc)
            }
            result
        }
        val s = createConcurrentStore(reducer, S(), enhancer = applyMiddleware(doubling))
        s.dispatch(Inc)
        assertEquals(2, s.state.count)
    }

    @Test
    fun asConcurrent_repoints_an_existing_bare_store() {
        val bare = createStore(reducer, S())
        val s = bare.asConcurrent()
        s.dispatch(Inc)
        assertEquals(1, s.state.count)
    }
}
