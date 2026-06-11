package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the unsubscribe contract (hardening plan C5): after `unsubscribe()`
 * returns, no NEW callback invocation begins — including callbacks already
 * queued on a posting context. Deliberate divergence from core Redux's
 * snapshot delivery; closes test gap T4.
 */
class UnsubscribeSemanticsTest {

    private data class S(val count: Int = 0)
    private object Inc

    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    private fun storeWithQueue(): Pair<CallerSerializedStore<S>, QueueingNotificationContext> {
        val queue = QueueingNotificationContext()
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = queue,
            onError = LogAndContinue,
        )
        return store to queue
    }

    @Test
    fun queuedCallbackIsSkippedWhenUnsubscribedBeforeDrain() {
        val (store, queue) = storeWithQueue()
        var hits = 0
        val unsub = store.subscribe { hits++ }

        store.dispatch(Inc) // signal queued
        unsub() // unsubscribed before the queue drains
        queue.drain()

        assertEquals(0, hits, "a queued callback must not run after unsubscribe() returned")
    }

    @Test
    fun inlinePeerUnsubscribedByEarlierListenerInSameFanOutIsSkipped() {
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = NotificationContext.Inline,
            onError = LogAndContinue,
        )
        var bHits = 0
        lateinit var unsubB: () -> Any
        store.subscribe { unsubB() } // A unsubscribes its peer B mid-round
        unsubB = store.subscribe { bHits++ }

        store.dispatch(Inc)

        // Deliberate divergence from core Redux snapshot delivery: B was
        // unsubscribed by A in the same fan-out and is skipped (guaranteed on
        // the same thread — the active flag is checked at execution time).
        assertEquals(0, bHits)
    }

    @Test
    fun doubleUnsubscribeIsIdempotent() {
        val (store, queue) = storeWithQueue()
        var hits = 0
        val unsub = store.subscribe { hits++ }
        unsub()
        unsub()
        store.dispatch(Inc)
        queue.drain()
        assertEquals(0, hits)
    }

    @Test
    fun sameLambdaSubscribedTwiceLosesExactlyOneDeliveryPerUnsubscribe() {
        val (store, queue) = storeWithQueue()
        var hits = 0
        val listener: () -> Unit = { hits++ }
        store.subscribe(listener)
        val unsubSecond = store.subscribe(listener)

        unsubSecond()
        store.dispatch(Inc)
        queue.drain()

        assertEquals(1, hits, "unsubscribing one registration must not affect the other")
    }
}
