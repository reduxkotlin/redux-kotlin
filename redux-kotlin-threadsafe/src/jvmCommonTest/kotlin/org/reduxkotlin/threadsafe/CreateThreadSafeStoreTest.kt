package org.reduxkotlin.threadsafe

import kotlinx.coroutines.*
import org.junit.Test
import org.reduxkotlin.*
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals

class CreateThreadSafeStoreTest {
    private suspend fun massiveRun(numCoroutines: Int, numRepeats: Int, action: suspend () -> Unit) {
        val time = measureTimeMillis {
            coroutineScope {
                repeat(numCoroutines) {
                    launch {
                        repeat(numRepeats) { action() }
                    }
                }
            }
        }
        println("Completed ${numCoroutines * numRepeats} actions in $time ms")
    }

    @Test
    fun multithreadedIncrementsMassively() {
        // NOTE: changing this to createStore() breaks the tests
        val store = createThreadSafeStore(counterReducer, TestState())
        runBlocking {
            withContext(Dispatchers.Default) {
                massiveRun(100, 1000) {
                    store.dispatch(Increment())
                }
            }
            assertEquals(100000, store.state.counter)
        }
    }

    @Test
    fun multithreadedIncrementsMassivelyWithEnhancer() {
        val store = createStore(
            counterReducer,
            TestState(),
            compose(
                applyMiddleware(createTestThunkMiddleware()),
                // needs to be placed after enhancers that requires synchronized store methods
                createSynchronizedStoreEnhancer()
            )
        )
        runBlocking {
            withContext(Dispatchers.Default) {
                massiveRun(10, 100) {
                    store.dispatch(incrementThunk())
                }
            }
            // wait to assert to account for the last of thunk delays
            Timer().schedule(
                timerTask {
                    assertEquals(10000, store.state.counter)
                },
                50
            )
        }
    }
}

class Increment

data class TestState(val counter: Int = 0)

val counterReducer = { state: TestState, action: Any ->
    when (action) {
        is Increment -> state.copy(counter = state.counter + 1)
        else -> state
    }
}

// Enhancer mimics the behavior of `createThunkMiddleware` provided by the redux-kotlin-thunk library
typealias TestThunk<State> = (dispatch: Dispatcher, getState: GetState<State>, extraArg: Any?) -> Any

fun <State> createTestThunkMiddleware(): Middleware<State> = { store ->
    { next: Dispatcher ->
        { action: Any ->
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
            dispatch(Increment())
        },
        50
    )
    getState()
}
