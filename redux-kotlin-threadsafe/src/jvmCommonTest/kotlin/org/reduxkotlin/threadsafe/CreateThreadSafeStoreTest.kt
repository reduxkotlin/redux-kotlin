package org.reduxkotlin.threadsafe

import kotlinx.coroutines.*
import org.junit.Test
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import test.TestApp
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
        val store = createThreadSafeStore(TestApp.counterReducer, TestApp.TestState())
        runBlocking {
            withContext(Dispatchers.Default) {
                massiveRun(100, 1000) {
                    store.dispatch(TestApp.Increment)
                }
            }
            assertEquals(100000, store.state.counter)
        }
    }

    @Test
    fun multithreadedIncrementsMassivelyWithEnhancer() {
        val store = createStore(
            TestApp.counterReducer,
            TestApp.TestState(),
            compose(
                applyMiddleware(TestApp.createTestThunkMiddleware()),
                // needs to be placed after enhancers that requires synchronized store methods
                createSynchronizedStoreEnhancer()
            )
        )
        runBlocking {
            withContext(Dispatchers.Default) {
                massiveRun(10, 100) {
                    store.dispatch(TestApp.incrementThunk())
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
