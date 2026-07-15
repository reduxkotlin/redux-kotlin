package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins [coalescingNotificationContext]'s serial FIFO contract against a real
 * store. A scheduled drain may coalesce platform posts, never notifications.
 */
class CoalescingContextDeliveryTest {

    private data class S(val count: Int = 0)
    private object Inc

    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    @Test
    fun offTargetBurstUsesOneScheduledDrainAndDeliversEverySignal() {
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

        assertEquals(1, queued.size, "the burst needs one active scheduled drain")
        assertEquals(listOf(0, 0, 0), hits.toList(), "nothing delivered before the queue runs")

        queued.removeFirst()()
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
    fun workerThenTargetDispatchesRemainFifoAndPullLatestState() {
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
        store.dispatch(Inc) // joins the older worker signal
        assertEquals(emptyList(), seen, "target work must not overtake the queued worker signal")
        queued.removeFirst()()

        assertEquals(listOf(2, 2), seen, "callbacks pull current state — a signal is not a payload")
    }

    @Test
    fun reentrantPostRunsAfterCurrentCallbackWithoutRecursion() {
        val delivered = mutableListOf<String>()
        var depth = 0
        var maxDepth = 0
        val context = coalescingNotificationContext(
            isOnTargetThread = { true },
            post = { error("an idle target-thread post must not schedule") },
        )

        context.post {
            depth++
            maxDepth = maxOf(maxDepth, depth)
            delivered += "outer-start"
            context.post {
                depth++
                maxDepth = maxOf(maxDepth, depth)
                delivered += "nested"
                depth--
            }
            delivered += "outer-end"
            depth--
        }

        assertEquals(listOf("outer-start", "outer-end", "nested"), delivered)
        assertEquals(1, maxDepth)
    }

    @Test
    fun callbackFailureDoesNotStrandQueuedOrFutureWork() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        val context = coalescingNotificationContext(
            isOnTargetThread = { false },
            post = { scheduled.addLast(it) },
        )

        context.post { error("boom") }
        context.post { delivered += "queued" }
        assertFailsWith<IllegalStateException> { scheduled.removeFirst()() }
        assertEquals(listOf("queued"), delivered)

        context.post { delivered += "future" }
        scheduled.removeFirst()()
        assertEquals(listOf("queued", "future"), delivered)
    }

    @Test
    fun schedulerFailureReleasesClaimForRetry() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<String>()
        var reject = true
        val context = coalescingNotificationContext(
            isOnTargetThread = { false },
            post = { callback ->
                if (reject) error("target rejected notification delivery")
                scheduled.addLast(callback)
            },
        )

        assertFailsWith<IllegalStateException> { context.post { delivered += "rejected" } }
        reject = false
        context.post { delivered += "retry" }
        assertEquals(1, scheduled.size)
        scheduled.removeFirst()()
        assertEquals(listOf("rejected", "retry"), delivered)
    }

    @Test
    fun boundedDrainYieldsAndKeepsNewArrivalsInOrder() {
        val scheduled = ArrayDeque<() -> Unit>()
        val delivered = mutableListOf<Int>()
        val context = coalescingNotificationContext(
            isOnTargetThread = { false },
            post = { scheduled.addLast(it) },
        )

        repeat(64) { value ->
            context.post {
                delivered += value
                if (value == 63) context.post { delivered += 64 }
            }
        }
        assertEquals(1, scheduled.size)

        scheduled.removeFirst()()
        assertEquals((0 until 64).toList(), delivered)
        assertEquals(1, scheduled.size, "a full batch must yield through a continuation")

        scheduled.removeFirst()()
        assertEquals((0..64).toList(), delivered)
        assertTrue(scheduled.isEmpty())
    }
}
