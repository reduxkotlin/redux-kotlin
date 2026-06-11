package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [coalescingNotificationContext]'s delivery arithmetic against a real
 * store (closes test gap T5): "coalescing" = inline-vs-marshal routing only —
 * bursts are never collapsed; every dispatch delivers one callback per
 * subscriber. Any future burst-coalescing becomes a deliberate, test-visible
 * change.
 */
class CoalescingContextDeliveryTest {

    private data class S(val count: Int = 0)
    private object Inc

    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    @Test
    fun offTargetBurstQueuesOneSignalPerSubscriberPerDispatch() {
        val queued = mutableListOf<() -> Unit>()
        val context = coalescingNotificationContext(
            isOnTargetThread = { false },
            post = { block -> queued += block },
        )
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = context,
            onError = LogAndContinue,
        )
        val hits = IntArray(3)
        repeat(3) { i -> store.subscribe { hits[i]++ } }

        repeat(5) { store.dispatch(Inc) }

        assertEquals(15, queued.size, "3 subscribers x 5 dispatches, no burst collapsing")
        assertEquals(listOf(0, 0, 0), hits.toList(), "nothing delivered before the queue runs")

        queued.forEach { it() }
        assertEquals(listOf(5, 5, 5), hits.toList(), "each subscriber exactly once per dispatch")
    }

    @Test
    fun onTargetCallbacksRunInlineAndQueueStaysEmpty() {
        val queued = mutableListOf<() -> Unit>()
        val context = coalescingNotificationContext(
            isOnTargetThread = { true },
            post = { block -> queued += block },
        )
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = context,
            onError = LogAndContinue,
        )
        var hits = 0
        var seen = -1
        store.subscribe {
            hits++
            seen = store.state.count
        }

        store.dispatch(Inc)

        assertEquals(1, hits, "on-target callback runs inline with the dispatch")
        assertEquals(1, seen)
        assertEquals(0, queued.size, "nothing marshals when already on the target thread")
    }

    @Test
    fun drainedOffTargetCallbackPullsLatestState() {
        val queued = mutableListOf<() -> Unit>()
        var onTarget = false
        val context = coalescingNotificationContext(
            isOnTargetThread = { onTarget },
            post = { block -> queued += block },
        )
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = context,
            onError = LogAndContinue,
        )
        val seen = mutableListOf<Int>()
        store.subscribe { seen += store.state.count }

        store.dispatch(Inc) // off-target: queued
        onTarget = true
        store.dispatch(Inc) // on-target: inline, reads 2
        queued.forEach { it() } // late drain of the first signal: also reads 2

        assertEquals(listOf(2, 2), seen, "callbacks pull current state — a signal is not a payload")
    }
}
