package org.reduxkotlin.bundle.concurrency

import org.reduxkotlin.bundle.CounterModel
import org.reduxkotlin.bundle.Increment
import org.reduxkotlin.bundle.counterInitial
import org.reduxkotlin.bundle.createConcurrentModelStore
import org.reduxkotlin.bundle.onIncrement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

/** JVM concurrency stress: verifies the concurrent model store serializes dispatch correctly. */
class ConcurrentModelStoreStressTest {
    @Test
    fun concurrent_dispatch_does_not_lose_writes() {
        val store = createConcurrentModelStore {
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
