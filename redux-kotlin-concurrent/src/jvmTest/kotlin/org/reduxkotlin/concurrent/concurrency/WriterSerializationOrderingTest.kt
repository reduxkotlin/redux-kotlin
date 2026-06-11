package org.reduxkotlin.concurrent.concurrency

import org.reduxkotlin.concurrent.CallerSerializedStore
import org.reduxkotlin.concurrent.LogAndContinue
import org.reduxkotlin.concurrent.NotificationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tripwire (hardening plan C7): with the Inline context, subscriber callbacks
 * run while the writer lock is held — dispatcher A's parked subscriber must
 * complete strictly before dispatcher B's reducer runs. Any future
 * fan-out-off-the-lock change flips this ordering consciously (and must then
 * revisit granular's serial-delivery assumption — see
 * GranularOnConcurrentStoreStressTest in redux-kotlin-granular).
 *
 * Latch-choreographed event log; no timing assertions.
 */
class WriterSerializationOrderingTest {

    private data class S(val count: Int = 0)
    private object MarkA
    private object MarkB

    private val awaitSeconds = 30L

    @Test
    fun parkedInlineSubscriberCompletesBeforeTheNextDispatchersReducerRuns() {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val subscriberParked = CountDownLatch(1)
        val release = CountDownLatch(1)

        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(
                { s: S, a ->
                    when (a) {
                        is MarkA -> s.copy(count = s.count + 1)

                        is MarkB -> {
                            events += "B-reduce"
                            s.copy(count = s.count + 1)
                        }

                        else -> s
                    }
                },
                S(),
            ),
            notificationContext = NotificationContext.Inline,
            onError = LogAndContinue,
        )

        var parkedOnce = false
        store.subscribe {
            if (!parkedOnce) {
                parkedOnce = true
                events += "A-sub-start"
                subscriberParked.countDown()
                check(release.await(awaitSeconds, TimeUnit.SECONDS)) { "release latch timed out" }
                events += "A-sub-end"
            }
        }

        val dispatcherA = thread(isDaemon = true) { store.dispatch(MarkA) }
        assertTrue(subscriberParked.await(awaitSeconds, TimeUnit.SECONDS), "A's subscriber never parked")

        val dispatcherB = thread(isDaemon = true) { store.dispatch(MarkB) }
        // Confirm B is genuinely blocked on the writer lock before releasing A
        // (bounded thread-state poll — a liveness wait, not a timing assertion).
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(awaitSeconds)
        while (dispatcherB.state != Thread.State.BLOCKED && dispatcherB.state != Thread.State.WAITING) {
            check(System.nanoTime() < deadline) { "dispatcher B never blocked on the writer lock" }
            check(dispatcherB.isAlive || events.contains("B-reduce")) { "dispatcher B died unexpectedly" }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
        }

        release.countDown()
        dispatcherA.join(TimeUnit.SECONDS.toMillis(awaitSeconds))
        dispatcherB.join(TimeUnit.SECONDS.toMillis(awaitSeconds))

        assertEquals(
            listOf("A-sub-start", "A-sub-end", "B-reduce"),
            events.toList(),
            "Inline fan-out must hold the writer lock: A's subscriber completes before B's reducer",
        )
        assertEquals(2, store.state.count)
    }
}
