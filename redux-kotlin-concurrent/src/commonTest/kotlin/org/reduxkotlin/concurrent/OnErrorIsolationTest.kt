package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins onError self-shielding (hardening plan C6): a throwing `onError`
 * handler must not abort delivery to remaining subscribers nor escape
 * `dispatch` — the module's own promise ("never aborts delivery").
 */
class OnErrorIsolationTest {

    private data class S(val count: Int = 0)
    private object Inc

    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    @Test
    fun throwingOnErrorHandlerIsSwallowedAndDeliveryProceeds() {
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = NotificationContext.Inline,
            onError = { throw IllegalArgumentException("handler is itself broken") },
        )
        var goodHits = 0
        store.subscribe { throw IllegalStateException("boom") }
        store.subscribe { goodHits++ }

        store.dispatch(Inc) // must return normally despite listener AND handler throwing

        assertEquals(1, goodHits, "delivery to remaining subscribers must proceed")
        assertEquals(1, store.state.count, "state and mirror stay consistent")
    }

    @Test
    fun throwingOnErrorHandlerSurvivesQueuedDrainToo() {
        val queue = QueueingNotificationContext()
        val store = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = queue,
            onError = { throw IllegalArgumentException("handler is itself broken") },
        )
        var goodHits = 0
        store.subscribe { throw IllegalStateException("boom") }
        store.subscribe { goodHits++ }

        store.dispatch(Inc)
        queue.drain() // must complete despite the double fault

        assertEquals(1, goodHits)
        assertEquals(1, store.state.count)
    }
}
