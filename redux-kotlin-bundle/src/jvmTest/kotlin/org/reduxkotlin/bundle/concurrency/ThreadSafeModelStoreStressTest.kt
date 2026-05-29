package org.reduxkotlin.bundle.concurrency

import org.reduxkotlin.bundle.CounterModel
import org.reduxkotlin.bundle.Increment
import org.reduxkotlin.bundle.counterInitial
import org.reduxkotlin.bundle.createThreadSafeModelStore
import org.reduxkotlin.bundle.onIncrement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

/** JVM concurrency stress: verifies the thread-safe model store serializes dispatch correctly. */
class ThreadSafeModelStoreStressTest {
    @Test
    fun concurrent_dispatch_through_thread_safe_wrapper_does_not_lose_writes() {
        val store = createThreadSafeModelStore {
            model(counterInitial()) { on<Increment> { s, a -> onIncrement(s, a) } }
        }
        val threads = 8
        val perThread = 1000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.submit {
                start.await()
                repeat(perThread) { store.dispatch(Increment(1)) }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()
        assertEquals(threads * perThread, store.state.get<CounterModel>().count)
    }
}
