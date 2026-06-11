package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Store-level behavior under a real (queued) posting context — closes test gap
 * T2: every prior store test used [NotificationContext.Inline].
 */
class QueuedNotificationStoreTest {

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
    fun stateIsCurrentImmediatelyWhileCallbacksWaitForDrain() {
        val (store, queue) = storeWithQueue()
        val hits = mutableMapOf("a" to 0, "b" to 0, "c" to 0)
        val seenAtCallback = mutableListOf<Int>()
        listOf("a", "b", "c").forEach { key ->
            store.subscribe {
                hits[key] = hits.getValue(key) + 1
                seenAtCallback += store.state.count
            }
        }

        store.dispatch(Inc)

        // Writes are synchronous; notification rides the (queued) context.
        assertEquals(1, store.state.count, "state must be current before drain")
        assertEquals(0, seenAtCallback.size, "no callback may run before drain")
        assertEquals(3, queue.pending, "one queued block per subscriber per dispatch")

        queue.drain()
        assertEquals(listOf(1, 1, 1), hits.values.toList(), "each subscriber exactly once")
        assertEquals(listOf(1, 1, 1), seenAtCallback, "each callback reads the published state")
    }

    @Test
    fun burstDeliversEverySignalAndCallbacksPullLatestState() {
        val (store, queue) = storeWithQueue()
        var hits = 0
        val seen = mutableListOf<Int>()
        store.subscribe {
            hits++
            seen += store.state.count
        }

        store.dispatch(Inc)
        store.dispatch(Inc)
        assertEquals(2, queue.pending, "no burst collapsing: one signal per dispatch")

        queue.drain()
        assertEquals(2, hits)
        // Signal-not-payload: both callbacks read the final state.
        assertEquals(listOf(2, 2), seen)
    }

    @Test
    fun nestedDispatchFromDrainedCallbackEnqueuesNextRound() {
        val (store, queue) = storeWithQueue()
        var redispatched = false
        var hits = 0
        store.subscribe {
            hits++
            if (!redispatched) {
                redispatched = true
                store.dispatch(Inc)
            }
        }

        store.dispatch(Inc)
        queue.drain() // drains the first signal AND the nested dispatch's signal

        assertEquals(2, store.state.count)
        assertEquals(2, hits)
    }

    @Test
    fun replaceReducerPublishesMirrorBeforeDrainAndSignalsOnce() {
        val (store, queue) = storeWithQueue()
        var hits = 0
        store.subscribe { hits++ }

        store.replaceReducer { st, a -> if (a is Inc) st.copy(count = st.count + 10) else st.copy(count = 42) }

        assertEquals(42, store.state.count, "mirror published before the queued signals run")
        assertEquals(0, hits)
        queue.drain()
        assertEquals(1, hits, "REPLACE delivers one signal per subscriber")
    }
}
