package org.reduxkotlin.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Test
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.TestState
import org.reduxkotlin.Todo
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createSameThreadEnforcedStore
import org.reduxkotlin.middleware
import org.reduxkotlin.todos
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreateSameThreadEnforcedStoreSpec {
    lateinit var store: Store<TestState>

    @BeforeTest
    fun before() {
        val mainThreadSurrogate = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(mainThreadSurrogate)

        store = createSameThreadEnforcedStore(
            todos,
            TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )
    }

    @Test
    fun ensureSameThreadOnGetState() {
        ensureSameThread { store.getState() }
    }

    @Test
    fun ensureSameThreadOnDispatch() {
        ensureSameThread { store.dispatch(Any()) }
    }

    @Test
    fun ensureSameThreadOnReplaceReducer() {
        ensureSameThread { store.replaceReducer { state, action -> state } }
    }

    @Test
    fun ensureSameThreadOnSubscribe() {
        ensureSameThread { store.subscribe { } }
    }

    @Test
    fun enforcesSameThreadWhenThreadNameAppendsCoroutineName() {
        val middleware = TestMiddleware()

        runBlocking {
            CoroutineScope(Dispatchers.Main).async {
                val store = createSameThreadEnforcedStore(
                    testReducer,
                    TestState(),
                    applyMiddleware(middleware.middleware)
                )

                store.dispatch(Any())
            }.await()
            Thread.sleep(2000)
            assertFalse(middleware.failed)
        }
    }

    @Test
    fun incrementsMassively() {
        suspend fun massiveRun(action: suspend () -> Unit) {
            val n = 100 // number of coroutines to launch
            val k = 1000 // times an action is repeated by each coroutine
            val time = measureTimeMillis {
                coroutineScope {
                    // scope for coroutines
                    repeat(n) {
                        launch {
                            repeat(k) { action() }
                        }
                    }
                }
            }
            println("Completed ${n * k} actions in $time ms")
        }

        val counterContext = newSingleThreadContext("CounterContext")

        lateinit var store: Store<TestCounterState>
        runBlocking {
            withContext(counterContext) {
                store = createSameThreadEnforcedStore(counterReducer, TestCounterState())
            }
        }
        runBlocking {
            withContext(counterContext) {
                massiveRun {
                    store.dispatch(Increment())
                }
            }
            withContext(counterContext) {
                assertEquals(100000, store.state.counter)
            }
        }
    }
}

private fun ensureSameThread(testFun: () -> Any) {
    val latch = CountDownLatch(1)
    var exception: java.lang.IllegalStateException? = null
    var state: Any? = null

    val newThread = Thread {
        state = testFun()
    }

    newThread.setUncaughtExceptionHandler { thread, throwable ->
        exception = throwable as IllegalStateException
        latch.countDown()
    }
    newThread.start()

    latch.await()

    assertNotNull(exception)
    assertNull(state)
}

val testReducer: Reducer<TestState> = { state, action -> state }

/**
 * Used as a test for when Thread.currentThread.name returns the
 * thread name + '@coroutine#'.
 * See issue #38 https://github.com/reduxkotlin/redux-kotlin/issues/38
 */
class TestMiddleware {
    var failed = false
    val middleware = middleware<TestState> { store, next, action ->
        CoroutineScope(Dispatchers.Main).launch {
            flow {
                delay(1000) // simulate api call
                emit("Text Response")
            }.collect { response ->
                store.dispatch("")
            }
        }
        try {
            next(action)
        } catch (e: Exception) {
            e.printStackTrace()
            failed = true
            Unit
        }
    }
}

class Increment

data class TestCounterState(val counter: Int = 0)

val counterReducer = { state: TestCounterState, action: Any ->
    when (action) {
        is Increment -> state.copy(counter = state.counter + 1)
        else -> state
    }
}
