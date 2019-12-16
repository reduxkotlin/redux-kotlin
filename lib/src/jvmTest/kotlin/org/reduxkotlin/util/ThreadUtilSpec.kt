package org.reduxkotlin.util

import org.reduxkotlin.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.CountDownLatch
import kotlin.IllegalStateException
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object ThreadUtilSpec : Spek({
    describe("createStore") {
        val store = createStore(
            todos, TestState(
                listOf(
                    Todo(
                        id = "1",
                        text = "Hello"
                    )
                )
            )
        )

        it("ensure same thread on getState") {
            ensureSameThread { store.getState() }
        }
        it("ensure same thread on dispatch") {
            ensureSameThread { store.dispatch(Any()) }
        }
        it("ensure same thread on replaceReducer") {
            ensureSameThread { store.replaceReducer { state, action ->  state } }
        }
        it("ensure same thread on subscribe") {
            ensureSameThread { store.subscribe { } }
        }
    }
})

private fun ensureSameThread(getState: () -> Any) {
    val latch = CountDownLatch(1)
    var exception: java.lang.IllegalStateException? = null
    var state: Any? = null

    val newThread = Thread {
        state = getState()
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