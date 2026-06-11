package org.reduxkotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DispatchDuringReduceTest {

    private data class CounterState(val count: Int = 0)
    private object Inc
    private object TriggerNestedDispatch
    private object TriggerReplaceReducer

    private fun storeWithSelfReference(): Store<CounterState> {
        lateinit var store: Store<CounterState>
        val reducer: Reducer<CounterState> = { state, action ->
            when (action) {
                is Inc -> state.copy(count = state.count + 1)

                is TriggerNestedDispatch -> {
                    store.dispatch(Inc)
                    state
                }

                is TriggerReplaceReducer -> {
                    store.replaceReducer { s, _ -> s }
                    state
                }

                else -> state
            }
        }
        store = createStore(reducer, CounterState())
        return store
    }

    @Test
    fun dispatchFromReducerThrowsAndLeavesStateUnchanged() {
        val store = storeWithSelfReference()

        val e = assertFailsWith<IllegalStateException> {
            store.dispatch(TriggerNestedDispatch)
        }

        assertTrue(
            e.message.orEmpty().contains("may not dispatch while state is being reduced"),
            "unexpected message: ${e.message}",
        )
        assertEquals(0, store.state.count, "state must be unchanged after the aborted dispatch")

        // The store stays usable after the failed dispatch.
        store.dispatch(Inc)
        assertEquals(1, store.state.count)
    }

    @Test
    fun replaceReducerFromReducerThrows() {
        val store = storeWithSelfReference()

        assertFailsWith<IllegalStateException> {
            store.dispatch(TriggerReplaceReducer)
        }
        assertEquals(0, store.state.count)
    }

    @Test
    fun dispatchFromListenerStillSucceeds() {
        val store = storeWithSelfReference()
        var redispatched = false

        store.subscribe {
            if (!redispatched) {
                redispatched = true
                store.dispatch(Inc)
            }
        }

        store.dispatch(Inc)
        assertEquals(2, store.state.count, "listener-time dispatch is allowed (reduction finished)")
    }
}
