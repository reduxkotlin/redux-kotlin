package test

import org.reduxkotlin.*
import java.util.*
import kotlin.concurrent.timerTask

// Enhancer mimics the behavior of `createThunkMiddleware` provided by the redux-kotlin-thunk library
typealias TestThunk<State> = (dispatch: Dispatcher, getState: GetState<State>, extraArg: Any?) -> Any

object TestApp {
    sealed interface TestAction
    object Increment : TestAction

    data class TestState(val counter: Int = 0)

    val counterReducer = { state: TestState, action: Any ->
        when (action) {
            is Increment -> state.copy(counter = state.counter + 1)
            else -> state
        }
    }

    fun <State> createTestThunkMiddleware(): Middleware<State> = { store ->
        {
                next: Dispatcher ->
            {
                    action: Any ->
                if (action is Function<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val thunk = try {
                        (action as TestThunk<*>)
                    } catch (e: ClassCastException) {
                        throw IllegalArgumentException("Require type TestThunk", e)
                    }
                    thunk(store.dispatch, store.getState, null)
                } else {
                    next(action)
                }
            }
        }
    }

    fun incrementThunk(): TestThunk<TestState> = { dispatch, getState, _ ->
        Timer().schedule(
            timerTask {
                dispatch(Increment)
            },
            50
        )
        getState()
    }
}
