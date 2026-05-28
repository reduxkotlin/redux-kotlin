package org.reduxkotlin.concurrent

import org.reduxkotlin.Reducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallerSerializedStoreTest {

    private data class S(val count: Int = 0)
    private object Inc
    private object Noop
    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    private fun store(onError: (Throwable) -> Unit = LogAndContinue) = CallerSerializedStore(
            inner = org.reduxkotlin.createStore(reducer, S()),
            notificationContext = NotificationContext.Inline,
            onError = onError,
        )

    @Test
    fun dispatch_updates_state_visible_via_getState() {
        val s = store()
        s.dispatch(Inc)
        s.dispatch(Inc)
        assertEquals(2, s.state.count)
    }

    @Test
    fun subscriber_is_notified_and_sees_new_state() {
        val s = store()
        var seen = -1
        s.subscribe { seen = s.state.count }
        s.dispatch(Inc)
        assertEquals(1, seen)
    }

    @Test
    fun unsubscribe_stops_notifications() {
        val s = store()
        var hits = 0
        val unsub = s.subscribe { hits++ }
        s.dispatch(Inc)
        unsub()
        s.dispatch(Inc)
        assertEquals(1, hits)
    }

    @Test
    fun listener_dispatching_inline_runs_synchronously_and_sees_latest() {
        val s = store()
        val observed = mutableListOf<Int>()
        var reentered = false
        s.subscribe {
            observed.add(s.state.count)
            if (!reentered && s.state.count == 1) {
                reentered = true
                s.dispatch(Inc) // nested dispatch from a listener
            }
        }
        s.dispatch(Inc)
        assertEquals(2, s.state.count)
        assertTrue(observed.contains(2), "Listener should observe the nested update")
    }

    @Test
    fun throwing_listener_is_isolated_and_mirror_stays_consistent() {
        val errors = mutableListOf<Throwable>()
        val s = store(onError = { errors.add(it) })
        var goodHits = 0
        s.subscribe { throw IllegalStateException("boom") }
        s.subscribe { goodHits++ }
        s.dispatch(Inc)
        assertEquals(1, goodHits)
        assertEquals(1, errors.size)
        assertEquals(1, s.state.count)
    }

    @Test
    fun noop_action_keeps_state_and_still_notifies() {
        val s = store()
        var hits = 0
        s.subscribe { hits++ }
        s.dispatch(Noop)
        assertEquals(0, s.state.count)
        assertEquals(1, hits)
    }

    @Test
    fun replaceReducer_runs_through_the_sequencer() {
        val s = store()
        s.dispatch(Inc)
        var hits = 0
        s.subscribe { hits++ }
        s.replaceReducer { st, a -> if (a is Inc) st.copy(count = st.count + 10) else st }
        assertEquals(1, hits, "REPLACE should notify the wrapper's listeners exactly once")
        s.dispatch(Inc)
        assertEquals(11, s.state.count)
        assertEquals(2, hits)
    }
}
