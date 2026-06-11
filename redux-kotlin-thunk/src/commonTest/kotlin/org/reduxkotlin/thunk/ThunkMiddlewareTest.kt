package org.reduxkotlin.thunk

import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThunkMiddlewareTest {
    data class TestState(val counter: Int = 0)

    object Increment

    private val counterReducer = { state: TestState, action: Any ->
        when (action) {
            is Increment -> state.copy(counter = state.counter + 1)
            else -> state
        }
    }

    private fun store(extraArgument: Any? = null) = createStore(
        counterReducer,
        TestState(),
        applyMiddleware(createThunkMiddleware(extraArgument)),
    )

    @Test
    fun thunkIsInvokedWithDispatchAndGetState() {
        val store = store()
        var observedCounter = -1
        val thunk: Thunk<TestState> = { dispatch, getState, _ ->
            dispatch(Increment)
            observedCounter = getState().counter
        }

        store.dispatch(thunk)

        assertEquals(1, observedCounter)
        assertEquals(1, store.state.counter)
    }

    @Test
    fun thunkReturnValueIsReturnedFromDispatch() {
        val store = store()
        val thunk: Thunk<TestState> = { _, _, _ -> "thunk-result" }

        assertEquals("thunk-result", store.dispatch(thunk))
    }

    @Test
    fun nonFunctionActionsPassThroughToReducer() {
        val store = store()

        store.dispatch(Increment)
        store.dispatch(Increment)

        assertEquals(2, store.state.counter)
    }

    @Test
    fun extraArgumentIsForwardedToThunk() {
        val api = object {
            val name = "fake-api"
        }
        val store = store(extraArgument = api)
        var received: Any? = null
        val thunk: Thunk<TestState> = { _, _, extraArg ->
            received = extraArg
        }

        store.dispatch(thunk)

        assertTrue(received === api)
    }

    @Test
    fun thunkCanDispatchAnotherThunk() {
        val store = store()
        val inner: Thunk<TestState> = { dispatch, _, _ -> dispatch(Increment) }
        val outer: Thunk<TestState> = { dispatch, _, _ ->
            dispatch(Increment)
            dispatch(inner)
        }

        store.dispatch(outer)

        assertEquals(2, store.state.counter)
    }
}
