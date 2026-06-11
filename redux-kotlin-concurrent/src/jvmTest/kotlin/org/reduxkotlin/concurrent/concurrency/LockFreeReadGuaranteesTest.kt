package org.reduxkotlin.concurrent.concurrency

import org.reduxkotlin.concurrent.CallerSerializedStore
import org.reduxkotlin.concurrent.LogAndContinue
import org.reduxkotlin.concurrent.NotificationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the lock-free read guarantee (test gap T1) and the publish point (C1):
 * while a dispatch holds the writer lock — parked in a reducer or in an Inline
 * subscriber — `getState()` and `subscribe()` on another thread return
 * promptly, and what `getState()` returns is determined by the publish point
 * (before listener fan-out, after the reducer commits).
 *
 * Latch choreography only; generous awaits; assertions on completion, never
 * on latency.
 */
class LockFreeReadGuaranteesTest {

    private data class S(val count: Int = 0)
    private object Inc

    private val awaitSeconds = 30L

    @Test
    fun readsDoNotBlockAndSeeTheNewStateWhileAnInlineSubscriberHoldsTheLock() {
        val subscriberParked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(
                { s: S, a -> if (a is Inc) s.copy(count = s.count + 1) else s },
                S(),
            ),
            notificationContext = NotificationContext.Inline,
            onError = LogAndContinue,
        )
        store.subscribe {
            subscriberParked.countDown()
            check(release.await(awaitSeconds, TimeUnit.SECONDS)) { "release latch timed out" }
        }

        val dispatcher = thread(isDaemon = true) { store.dispatch(Inc) }
        assertTrue(subscriberParked.await(awaitSeconds, TimeUnit.SECONDS), "subscriber never parked")

        // Writer lock is held by the parked Inline fan-out. Reads and
        // subscriptions must complete without it.
        val readerDone = CountDownLatch(1)
        var observed = -1
        var subscribed = false
        val reader = thread(isDaemon = true) {
            observed = store.state.count
            store.subscribe { }
            subscribed = true
            readerDone.countDown()
        }
        assertTrue(
            readerDone.await(awaitSeconds, TimeUnit.SECONDS),
            "getState/subscribe blocked while the writer lock was held",
        )
        // Publish point: the mirror was published BEFORE the fan-out, so the
        // reader already sees the new state while the subscriber is parked.
        assertEquals(1, observed, "reader must see the published (new) state mid-fan-out")
        assertTrue(subscribed)

        release.countDown()
        dispatcher.join(TimeUnit.SECONDS.toMillis(awaitSeconds))
        reader.join(TimeUnit.SECONDS.toMillis(awaitSeconds))
        assertEquals(1, store.state.count)
    }

    @Test
    fun readsDoNotBlockAndSeeThePreviousStateWhileTheReducerIsRunning() {
        val reducerParked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(
                { s: S, a ->
                    if (a is Inc) {
                        reducerParked.countDown()
                        check(release.await(awaitSeconds, TimeUnit.SECONDS)) { "release latch timed out" }
                        s.copy(count = s.count + 1)
                    } else {
                        s
                    }
                },
                S(),
            ),
            notificationContext = NotificationContext.Inline,
            onError = LogAndContinue,
        )

        val dispatcher = thread(isDaemon = true) { store.dispatch(Inc) }
        assertTrue(reducerParked.await(awaitSeconds, TimeUnit.SECONDS), "reducer never parked")

        val readerDone = CountDownLatch(1)
        var observed = -1
        val reader = thread(isDaemon = true) {
            observed = store.state.count
            readerDone.countDown()
        }
        assertTrue(readerDone.await(awaitSeconds, TimeUnit.SECONDS), "getState blocked mid-reduce")
        // Mid-reduce there is nothing committed yet: the previous mirror is correct.
        assertEquals(0, observed, "mid-reduce reads return the previous published state")

        release.countDown()
        dispatcher.join(TimeUnit.SECONDS.toMillis(awaitSeconds))
        reader.join(TimeUnit.SECONDS.toMillis(awaitSeconds))
        assertEquals(1, store.state.count)
    }
}
