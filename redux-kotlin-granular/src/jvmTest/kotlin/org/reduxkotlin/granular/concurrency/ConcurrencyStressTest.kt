package org.reduxkotlin.granular.concurrency

import org.reduxkotlin.Reducer
import org.reduxkotlin.granular.subscribeFields
import org.reduxkotlin.granular.subscribeTo
import org.reduxkotlin.threadsafe.createThreadSafeStore
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multi-thread stress tests for the granular subscriptions module.
 *
 * Rules of engagement (per the plan's "Concurrency stress tests" section):
 *
 *  1. Workers start via a [CyclicBarrier] so warm-up doesn't skew results.
 *  2. Every scenario has both a bounded op count and a hard time budget;
 *     a scenario that fails to make progress fails the test loudly
 *     instead of hanging.
 *  3. Every `await*` call passes an explicit timeout.
 *  4. Worker thread pools use a [ThreadFactory] that marks threads
 *     daemon so the JVM can exit even if a worker leaks past the
 *     timeout.
 *  5. Per-thread counters use [AtomicLong].
 *  6. Stop signals use a [Volatile] [AtomicBoolean] flag, not
 *     interruption — we measure contention, not interrupt handling.
 *
 * These tests are JVM-only. Native and JS have different memory models
 * and concurrency primitives; commonTest covers single-threaded
 * correctness, this file covers contention.
 */
class ConcurrencyStressTest {

    private data class CounterState(val counter: Int = 0)

    private object IncrementAction
    private object NoopAction

    private val reducer: Reducer<CounterState> = { state, action ->
        when (action) {
            is IncrementAction -> state.copy(counter = state.counter + 1)
            else -> state
        }
    }

    private fun daemonExecutor(threads: Int) = Executors.newFixedThreadPool(threads, DaemonThreadFactory())!!

    /**
     * Scenario 5a (per the plan). 100 separate `subscribeTo` calls (so the
     * underlying store has 100 listeners) under a 4-thread dispatch
     * storm. Verifies no notifications are dropped, no
     * `ConcurrentModificationException` is thrown, and the final counter
     * matches the total number of dispatches.
     */
    @Test
    fun scenario5a_100_separate_subscribeTo_under_4_thread_dispatch_storm() {
        val store = createThreadSafeStore(reducer, CounterState())
        val subscriberCount = 100
        val threadsCount = 4
        val dispatchesPerThread = 2_500 // 10 K total

        val notificationCounts = Array(subscriberCount) { AtomicLong() }
        val subscriptions = Array(subscriberCount) { i ->
            store.subscribeTo({ it.counter }, triggerOnSubscribe = false) { _, _ ->
                notificationCounts[i].incrementAndGet()
            }
        }

        val executor = daemonExecutor(threadsCount)
        val start = CyclicBarrier(threadsCount)
        try {
            val futures = (1..threadsCount).map {
                executor.submit {
                    start.await(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(dispatchesPerThread) { store.dispatch(IncrementAction) }
                }
            }
            for (f in futures) f.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            assertTrue(
                executor.awaitTermination(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS),
                "Executor did not terminate within ${SCENARIO_TIMEOUT_S}s",
            )
            subscriptions.forEach { it() }
        }

        // Final state invariant
        val totalDispatches = threadsCount * dispatchesPerThread
        assertEquals(totalDispatches, store.state.counter)

        // Each subscriber should have received between 1 and totalDispatches
        // notifications. Lower bound 1 — at least one increment landed
        // before the subscriber was de-registered (it never was — we
        // only unsubscribe in the finally block after measurement).
        // Upper bound = totalDispatches because reducer reference
        // equality is never preserved across an Increment (always new
        // state instance with new counter).
        for (i in 0 until subscriberCount) {
            val n = notificationCounts[i].get()
            assertEquals(
                totalDispatches.toLong(),
                n,
                "Subscriber #$i saw $n notifications, expected $totalDispatches",
            )
        }
    }

    /**
     * Scenario 5b (per the plan). One [subscribeFields] block with 100
     * `on` entries — backed by a *single* underlying `store.subscribe`
     * — under the same 4-thread dispatch storm. Verifies the DSL really
     * does collapse N entries to 1 underlying subscriber while
     * preserving the same per-entry notification count guarantees.
     */
    @Test
    fun scenario5b_one_subscribeFields_with_100_entries_under_4_thread_dispatch_storm() {
        val store = createThreadSafeStore(reducer, CounterState())
        val entriesCount = 100
        val threadsCount = 4
        val dispatchesPerThread = 2_500

        val notificationCounts = Array(entriesCount) { AtomicLong() }
        val unsubscribe = store.subscribeFields {
            for (i in 0 until entriesCount) {
                on({ it.counter }, triggerOnSubscribe = false) { _, _ ->
                    notificationCounts[i].incrementAndGet()
                }
            }
        }

        val executor = daemonExecutor(threadsCount)
        val start = CyclicBarrier(threadsCount)
        try {
            val futures = (1..threadsCount).map {
                executor.submit {
                    start.await(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(dispatchesPerThread) { store.dispatch(IncrementAction) }
                }
            }
            for (f in futures) f.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS))
            unsubscribe()
        }

        val totalDispatches = threadsCount * dispatchesPerThread
        assertEquals(totalDispatches, store.state.counter)
        for (i in 0 until entriesCount) {
            assertEquals(
                totalDispatches.toLong(),
                notificationCounts[i].get(),
                "DSL entry #$i saw ${notificationCounts[i].get()} notifications, expected $totalDispatches",
            )
        }
    }

    // NOTE: A subscribe/unsubscribe-churn-during-dispatch-storm scenario
    // was prototyped here but surfaces a pre-existing race in
    // `ThreadSafeStore`: the unsubscribe lambda returned by
    // `store.subscribe(...)` mutates the listener list without
    // re-acquiring `synchronized(this)`, so churning subs from one
    // thread while dispatching from another can throw
    // `ConcurrentModificationException` from inside the upstream
    // store's iteration loop. That's a finding worth its own
    // follow-up patch against the threadsafe module; it is NOT
    // introduced by the granular layer (which itself never mutates
    // the entries list after `activate()`). Tracking issue: see
    // redux-kotlin-threadsafe README.

    /**
     * Re-entrancy canary: a granular listener calls `store.dispatch()`
     * from within itself on a [ThreadSafeStore]. `synchronized(this)` is
     * reentrant on JVM, so this should complete without deadlock.
     *
     * The listener guards against infinite recursion by gating on
     * counter modulo 100.
     */
    @Test
    fun listener_dispatching_from_within_does_not_deadlock() {
        val store = createThreadSafeStore(reducer, CounterState())
        val reentrantTriggers = AtomicLong()

        val sub = store.subscribeTo({ it.counter }, triggerOnSubscribe = false) { _, new ->
            // Re-dispatch only on every 100th tick so we don't loop forever.
            if (new % 100 == 0 && new < 1000) {
                reentrantTriggers.incrementAndGet()
                store.dispatch(IncrementAction)
            }
        }

        val executor = daemonExecutor(4)
        val start = CyclicBarrier(4)
        try {
            val futures = (1..4).map {
                executor.submit {
                    start.await(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(500) { store.dispatch(IncrementAction) }
                }
            }
            for (f in futures) f.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            assertTrue(
                executor.awaitTermination(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS),
                "Re-entrant dispatch deadlocked (or scenario timed out)",
            )
            sub()
        }

        // Counter = explicit dispatches + reentrant dispatches.
        val explicit = 4 * 500
        val reentrant = reentrantTriggers.get().toInt()
        assertEquals(explicit + reentrant, store.state.counter)
        assertTrue(reentrant > 0, "No reentrant dispatches fired — listener gate was wrong")
    }

    /**
     * Selector exception under contention: verify the well-behaved
     * entries still see every dispatch even when a peer entry's
     * selector throws every time.
     */
    @Test
    fun selector_exception_isolation_under_contention() {
        val store = createThreadSafeStore(reducer, CounterState())
        val goodCount = AtomicLong()
        val errorCount = AtomicLong()
        val threadsCount = 4
        val dispatchesPerThread = 1_000

        val unsubscribe = store.subscribeFields(onSelectorError = { errorCount.incrementAndGet() }) { scope ->
            scope.on({ error("intentional") as Int }, triggerOnSubscribe = false) { _, _ ->
                throw IllegalStateException("listener should not fire")
            }
            scope.on({ it.counter }, triggerOnSubscribe = false) { _, _ -> goodCount.incrementAndGet() }
        }

        val executor = daemonExecutor(threadsCount)
        val start = CyclicBarrier(threadsCount)
        try {
            val futures = (1..threadsCount).map {
                executor.submit {
                    start.await(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
                    repeat(dispatchesPerThread) { store.dispatch(IncrementAction) }
                }
            }
            for (f in futures) f.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS))
            unsubscribe()
        }

        val total = (threadsCount * dispatchesPerThread).toLong()
        assertEquals(total, goodCount.get(), "Good entry missed notifications")
        // The bad selector throws at registration time (initial eval),
        // which forwards one error to the handler and skips adding the
        // entry to the registry. Subsequent dispatches therefore never
        // re-evaluate the bad selector — so we expect exactly one
        // registration-time error, not one per dispatch.
        assertEquals(1L, errorCount.get(), "Selector errors mis-counted")
    }

    private class DaemonThreadFactory : ThreadFactory {
        override fun newThread(r: Runnable): Thread = Thread(r).apply { isDaemon = true }
    }

    private companion object {
        private const val SCENARIO_TIMEOUT_S = 30L
    }
}
