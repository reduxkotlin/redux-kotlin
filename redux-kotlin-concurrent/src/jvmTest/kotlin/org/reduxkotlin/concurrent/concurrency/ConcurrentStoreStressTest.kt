package org.reduxkotlin.concurrent.concurrency

import org.reduxkotlin.Reducer
import org.reduxkotlin.concurrent.createConcurrentStore
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrentStoreStressTest {

    private data class CounterState(val counter: Int = 0, val perThread: Map<Int, Int> = emptyMap())
    private object Increment
    private data class Seq(val threadId: Int, val localSeq: Int)

    private val reducer: Reducer<CounterState> = { s, a ->
        when (a) {
            is Increment -> s.copy(counter = s.counter + 1)
            is Seq -> s.copy(perThread = s.perThread + (a.threadId to a.localSeq))
            else -> s
        }
    }

    private fun executor(n: Int) = Executors.newFixedThreadPool(n, DaemonThreadFactory())!!

    @Test
    fun no_lost_updates_under_dispatch_storm() {
        val store = createConcurrentStore(reducer, CounterState())
        val threads = 4
        val perThread = 5_000
        val exec = executor(threads)
        val start = CyclicBarrier(threads)
        try {
            val futures = (1..threads).map {
                exec.submit {
                    start.await(TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(perThread) { store.dispatch(Increment) }
                }
            }
            futures.forEach { it.get(TIMEOUT_S, TimeUnit.SECONDS) }
        } finally {
            exec.shutdown()
            assertTrue(exec.awaitTermination(TIMEOUT_S, TimeUnit.SECONDS))
        }
        assertEquals(threads * perThread, store.state.counter)
    }

    @Test
    fun reducer_is_never_re_entered_concurrently() {
        val active = AtomicLong(0)
        val violations = AtomicLong(0)
        val serializationReducer: Reducer<CounterState> = { s, a ->
            if (active.incrementAndGet() != 1L) violations.incrementAndGet()
            var x = 0
            repeat(50) { x += it }
            active.decrementAndGet()
            if (a is Increment) s.copy(counter = s.counter + 1 + (x and 0)) else s
        }
        val store = createConcurrentStore(serializationReducer, CounterState())
        val threads = 6
        val perThread = 2_000
        val exec = executor(threads)
        val start = CyclicBarrier(threads)
        try {
            val futures = (1..threads).map {
                exec.submit {
                    start.await(TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(perThread) { store.dispatch(Increment) }
                }
            }
            futures.forEach { it.get(TIMEOUT_S, TimeUnit.SECONDS) }
        } finally {
            exec.shutdown()
            assertTrue(exec.awaitTermination(TIMEOUT_S, TimeUnit.SECONDS))
        }
        assertEquals(0L, violations.get(), "Reducer was entered concurrently — serialization broken")
        assertEquals(threads * perThread, store.state.counter)
    }

    @Test
    fun per_producer_fifo_is_preserved() {
        val store = createConcurrentStore(reducer, CounterState())
        val threads = 4
        val perThread = 1_000
        val exec = executor(threads)
        val start = CyclicBarrier(threads)
        try {
            val futures = (1..threads).map { tid ->
                exec.submit {
                    start.await(TIMEOUT_S, TimeUnit.SECONDS)
                    for (i in 0 until perThread) store.dispatch(Seq(tid, i))
                }
            }
            futures.forEach { it.get(TIMEOUT_S, TimeUnit.SECONDS) }
        } finally {
            exec.shutdown()
            assertTrue(exec.awaitTermination(TIMEOUT_S, TimeUnit.SECONDS))
        }
        for (tid in 1..threads) {
            assertEquals(
                perThread - 1,
                store.state.perThread[tid],
                "Thread $tid's last write must win — per-producer order violated",
            )
        }
    }

    @Test
    fun subscribe_unsubscribe_churn_during_storm_does_not_throw() {
        val store = createConcurrentStore(reducer, CounterState())
        val dispatchers = 4
        val churners = 2
        val total = dispatchers + churners
        val failures = AtomicLong()
        val exec = executor(total)
        val start = CyclicBarrier(total)
        try {
            val df = (1..dispatchers).map {
                exec.submit {
                    start.await(TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(2_000) {
                        runCatching { store.dispatch(Increment) }.onFailure { failures.incrementAndGet() }
                    }
                }
            }
            val cf = (1..churners).map {
                exec.submit {
                    start.await(TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(2_000) {
                        runCatching {
                            val unsub = store.subscribe { }
                            unsub()
                        }.onFailure { failures.incrementAndGet() }
                    }
                }
            }
            (df + cf).forEach { it.get(TIMEOUT_S, TimeUnit.SECONDS) }
        } finally {
            exec.shutdown()
            assertTrue(exec.awaitTermination(TIMEOUT_S, TimeUnit.SECONDS))
        }
        assertEquals(0L, failures.get(), "Concurrent dispatch/subscribe churn raced")
        assertEquals(dispatchers * 2_000, store.state.counter)
    }

    @Test
    fun mirror_matches_inner_after_throwing_listener_under_load() {
        val errors = AtomicLong()
        val store = createConcurrentStore(reducer, CounterState(), onError = { errors.incrementAndGet() })
        store.subscribe { throw IllegalStateException("boom") }
        val threads = 4
        val perThread = 2_000
        val exec = executor(threads)
        val start = CyclicBarrier(threads)
        try {
            val futures = (1..threads).map {
                exec.submit {
                    start.await(TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(perThread) { store.dispatch(Increment) }
                }
            }
            futures.forEach { it.get(TIMEOUT_S, TimeUnit.SECONDS) }
        } finally {
            exec.shutdown()
            assertTrue(exec.awaitTermination(TIMEOUT_S, TimeUnit.SECONDS))
        }
        assertEquals(threads * perThread, store.state.counter)
        assertTrue(errors.get() > 0, "Throwing listener should have been isolated and counted")
    }

    private companion object {
        private const val TIMEOUT_S = 30L
    }
}
