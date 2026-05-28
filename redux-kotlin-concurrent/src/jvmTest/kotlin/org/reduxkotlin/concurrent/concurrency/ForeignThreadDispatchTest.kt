package org.reduxkotlin.concurrent.concurrency

import org.reduxkotlin.Reducer
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.concurrent.createConcurrentStore
import org.reduxkotlin.middleware
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForeignThreadDispatchTest {

    private data class S(val count: Int = 0)
    private object Inc
    private val reducer: Reducer<S> = { s, a -> if (a is Inc) s.copy(count = s.count + 1) else s }

    @Test
    fun middleware_redispatch_from_a_foreign_thread_is_serialized() {
        val done = CountDownLatch(1)
        val foreignDispatches = 200
        val asyncRedispatch = middleware<S> { store, next, action ->
            val r = next(action)
            if (action is Inc && store.state.count == 1) {
                Thread {
                    repeat(foreignDispatches) { store.dispatch(Inc) }
                    done.countDown()
                }.apply { isDaemon = true }.start()
            }
            r
        }
        val store = createConcurrentStore(reducer, S(), enhancer = applyMiddleware(asyncRedispatch))
        store.dispatch(Inc)
        assertTrue(done.await(30, TimeUnit.SECONDS), "Foreign dispatch thread did not finish")
        assertEquals(1 + foreignDispatches, store.state.count, "Foreign-thread re-dispatch must not be lost")
    }

    @Test
    fun concurrent_main_and_foreign_dispatch_has_no_lost_updates() {
        val hits = AtomicLong()
        val store = createConcurrentStore(reducer, S())
        store.subscribe { hits.incrementAndGet() }
        val t1 = Thread { repeat(5_000) { store.dispatch(Inc) } }
        val t2 = Thread { repeat(5_000) { store.dispatch(Inc) } }
        t1.start(); t2.start()
        t1.join(30_000); t2.join(30_000)
        assertEquals(10_000, store.state.count)
        assertEquals(10_000L, hits.get(), "Every dispatch notifies exactly once (Inline context)")
    }
}
