package org.reduxkotlin.granular.concurrency

import org.reduxkotlin.Reducer
import org.reduxkotlin.concurrent.createConcurrentStore
import org.reduxkotlin.granular.subscribeTo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tripwire (hardening plan C4): granular diffing over the REAL concurrent
 * store. `Entry.last` is a read-compare-write that is only sound under serial
 * notification delivery; with the default Inline context that serialism comes
 * for free from the fan-out running under the writer lock. If anyone ever
 * moves the fan-out off the lock (or otherwise breaks serial delivery), this
 * test fails loudly with lost/duplicated diffs.
 *
 * (Scenario 5a/5b in ConcurrencyStressTest run against createThreadSafeStore;
 * granular-over-ConcurrentStore previously had zero coverage.)
 */
class GranularOnConcurrentStoreStressTest {

    private class DaemonThreadFactory : java.util.concurrent.ThreadFactory {
        override fun newThread(r: Runnable): Thread = Thread(r).apply { isDaemon = true }
    }

    private data class GridState(val cells: List<Int>)
    private data class IncCell(val index: Int)

    private val cellCount = 100
    private val threads = 4
    private val dispatchesPerThread = 2_500
    private val awaitSeconds = 30L

    private val reducer: Reducer<GridState> = { state, action ->
        if (action is IncCell) {
            state.copy(cells = state.cells.toMutableList().also { it[action.index] = it[action.index] + 1 })
        } else {
            state
        }
    }

    @Test
    fun hundredGranularSubscriptionsSurviveAFourThreadDispatchStorm() {
        val store = createConcurrentStore(reducer, GridState(List(cellCount) { 0 }))
        val selectorErrors = AtomicInteger(0)
        val lastObserved = AtomicReferenceArray<Int>(cellCount)
        val monotonicViolations = AtomicInteger(0)

        repeat(cellCount) { index ->
            lastObserved.set(index, 0)
            store.subscribeTo({ it.cells[index] }, triggerOnSubscribe = false) { old, new ->
                // Serial delivery makes each entry's stream of diffs strictly
                // monotonic for this counter; any lost or duplicated diff
                // breaks the chain.
                if (old != lastObserved.get(index) || new != old + 1) monotonicViolations.incrementAndGet()
                lastObserved.set(index, new)
            }
        }

        val barrier = CyclicBarrier(threads)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads, DaemonThreadFactory())
        try {
            repeat(threads) { t ->
                pool.execute {
                    try {
                        barrier.await(awaitSeconds, TimeUnit.SECONDS)
                        var seed = t + 1
                        repeat(dispatchesPerThread) {
                            seed = seed * 1_103_515_245 + 12_345
                            store.dispatch(IncCell((seed ushr 16).mod(cellCount)))
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") t2: Throwable) {
                        selectorErrors.incrementAndGet()
                        println("storm worker failed: $t2")
                    } finally {
                        done.countDown()
                    }
                }
            }
            assertTrue(done.await(awaitSeconds * 2, TimeUnit.SECONDS), "storm never finished")
        } finally {
            pool.shutdownNow()
        }

        val finalState = store.state
        assertEquals(threads * dispatchesPerThread, finalState.cells.sum(), "no lost reducer updates")
        assertEquals(0, selectorErrors.get(), "no worker/selector failures")
        assertEquals(0, monotonicViolations.get(), "no lost or duplicated diffs under contention")
        repeat(cellCount) { index ->
            assertEquals(
                finalState.cells[index],
                lastObserved.get(index),
                "entry $index missed its final notification (lost trailing wakeup)",
            )
        }
    }
}
