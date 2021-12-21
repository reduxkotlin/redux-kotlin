package org.reduxkotlin.util

import java.util.Timer
import kotlin.concurrent.timerTask
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.reduxkotlin.Dispatcher
import org.reduxkotlin.GetState
import org.reduxkotlin.Middleware
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import org.reduxkotlin.createSynchronizedStoreEnhancer
import org.reduxkotlin.createThreadSafeStore

class MultiThreadedClass {
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
      counterReducer, TestState(),
      compose(
        applyMiddleware(createTestThunkMiddleware()),
        createSynchronizedStoreEnhancer() // needs to be placed after enhancers that requires synchronized store methods
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

fun <State> createTestThunkMiddleware(): Middleware<State> =
  { store ->
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
