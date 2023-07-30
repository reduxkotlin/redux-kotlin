package org.reduxkotlin

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.setMain
import org.junit.Test
import test.TodoApp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import kotlin.test.*

class CreateSameThreadEnforcedStoreTest : AbstractCreateSameThreadEnforcedStoreTest<Any>(
    Any(),
    {
        createSameThreadEnforcedStore(
            TodoApp.todoReducer,
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )
    }
)

class CreateSameThreadEnforcedTypedStoreTest : AbstractCreateSameThreadEnforcedStoreTest<TodoApp.TodoAction>(
    TodoApp.DoNothing,
    {
        createTypedSameThreadEnforcedStore(
            TodoApp.typedTodoReducer,
            TodoApp.TodoState(
                listOf(
                    TodoApp.Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )
    }
)

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AbstractCreateSameThreadEnforcedStoreTest<A>(
    private val action: A,
    private val storeProvider: () -> TypedStore<TodoApp.TodoState, A>
) {
    private lateinit var store: TypedStore<TodoApp.TodoState, A>

    @BeforeTest
    fun before() {
        val mainThreadSurrogate = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(mainThreadSurrogate)

        store = storeProvider()
    }

    @Test
    fun ensureSameThreadOnGetState() {
        ensureSameThread { store.getState() }
    }

    @Test
    fun ensureSameThreadOnDispatch() {
        ensureSameThread { store.dispatch(action) }
    }

    @Test
    fun ensureSameThreadOnReplaceReducer() {
        ensureSameThread { store.replaceReducer { state, _ -> state } }
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
                    TodoApp.TodoState(),
                    applyMiddleware(middleware.middleware)
                )

                store.dispatch(Any())
            }.await()
            Thread.sleep(2000)
            assertFalse(middleware.failed)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
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

    newThread.setUncaughtExceptionHandler { _, throwable ->
        exception = throwable as IllegalStateException
        latch.countDown()
    }
    newThread.start()

    latch.await()

    assertNotNull(exception)
    assertNull(state)
}

val testReducer: Reducer<TodoApp.TodoState> = { state, _ -> state }

/**
 * Used as a test for when Thread.currentThread.name returns the
 * thread name + '@coroutine#'.
 * See issue #38 https://github.com/reduxkotlin/redux-kotlin/issues/38
 */
class TestMiddleware {
    var failed = false
    val middleware = middleware<TodoApp.TodoState> { store, next, action ->
        CoroutineScope(Dispatchers.Main).launch {
            flow {
                delay(1000) // simulate api call
                emit("Text Response")
            }.collect {
                store.dispatch("")
            }
        }
        try {
            next(action)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            failed = true
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
