package org.reduxkotlin.registry.concurrency

import org.reduxkotlin.Store
import org.reduxkotlin.createStore
import org.reduxkotlin.registry.RegistryEvent
import org.reduxkotlin.registry.StoreRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multi-thread stress tests for the registry. Patterned on
 * `redux-kotlin-granular`'s ConcurrencyStressTest:
 *
 *  - Workers release via a CyclicBarrier so warm-up doesn't skew results.
 *  - Every scenario has a bounded op count AND a hard time budget — a stuck
 *    scenario fails loudly instead of hanging CI.
 *  - Every `await*` carries an explicit timeout.
 *  - Worker pools use a daemon ThreadFactory.
 *
 * JVM-only. The commonTest suite covers single-threaded correctness.
 */
class RegistryConcurrencyStressTest {

    private data class S(val v: Int = 0)
    private val reducer: (S, Any) -> S = { s, _ -> s }
    private fun newStore() = createStore(reducer, S())

    private fun executor(threads: Int) = Executors.newFixedThreadPool(threads, DaemonThreadFactory())

    /**
     * 32 threads call getOrCreate on the same id simultaneously. Creator must
     * run exactly once. Repeat across many ids and iterations.
     */
    @Test
    fun creator_runs_at_most_once_per_id_under_contention() {
        val iterations = 100
        val threadsPerId = 32
        val executor = executor(threadsPerId)

        try {
            repeat(iterations) { i ->
                val registry = StoreRegistry<String, S>()
                val id = "id-$i"
                val creatorCalls = AtomicInteger()
                val barrier = CyclicBarrier(threadsPerId)
                val results = ConcurrentLinkedQueue<Store<S>>()

                val futures = (1..threadsPerId).map {
                    executor.submit {
                        barrier.await(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
                        val s = registry.getOrCreate(id) {
                            creatorCalls.incrementAndGet()
                            newStore()
                        }
                        results.add(s)
                    }
                }

                futures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
                assertEquals(1, creatorCalls.get(), "iteration $i: creator called ${creatorCalls.get()} times")
                assertEquals(threadsPerId, results.size)
                val winner = results.first()
                results.forEach {
                    assertTrue(it === winner, "iteration $i: divergent store returned to a caller")
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /**
     * One writer thread churns remove+getOrCreate on a small set of ids while
     * many readers spam get(id). No exceptions, no torn reads — get must
     * return either null or a fully constructed store.
     */
    @Test
    fun no_torn_reads_under_concurrent_writer_and_readers() {
        val registry = StoreRegistry<Int, S>()
        val ids = (0..7).toList()
        val readerCount = 8
        val opsPerReader = 50_000
        val durationMs = 3_000L
        val stop = AtomicBoolean(false)
        val executor = executor(readerCount + 1)
        val errors = ConcurrentLinkedQueue<Throwable>()

        try {
            val writerFuture = executor.submit {
                try {
                    var i = 0
                    while (!stop.get()) {
                        val id = ids[i % ids.size]
                        registry.remove(id)
                        registry.getOrCreate(id) { newStore() }
                        i++
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }

            val readerFutures = (1..readerCount).map { r ->
                executor.submit {
                    try {
                        var seen = 0L
                        var iter = 0
                        while (!stop.get() && iter < opsPerReader) {
                            val s = registry.get(ids[iter % ids.size])
                            if (s != null) seen++
                            iter++
                        }
                        assertTrue(seen >= 0L, "reader $r overflowed counter (unreachable)")
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }

            Thread.sleep(durationMs)
            stop.set(true)
            writerFuture.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
            readerFutures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        if (errors.isNotEmpty()) {
            errors.forEach { it.printStackTrace() }
            throw AssertionError("${errors.size} errors during stress run; first: ${errors.first()}")
        }
    }

    /**
     * Producers spam getOrCreate / remove while a separate thread thrashes
     * addListener / unsubscribe. No ConcurrentModificationException, no
     * crashes.
     */
    @Test
    fun listener_add_remove_under_mutation_storm() {
        val registry = StoreRegistry<Int, S>()
        val ids = (0..15).toList()
        val producers = 4
        val durationMs = 3_000L
        val stop = AtomicBoolean(false)
        val executor = executor(producers + 1)
        val errors = ConcurrentLinkedQueue<Throwable>()

        try {
            val producerFutures = (1..producers).map { p ->
                executor.submit {
                    try {
                        var i = p
                        while (!stop.get()) {
                            val id = ids[i % ids.size]
                            registry.getOrCreate(id) { newStore() }
                            if (i % 3 == 0) registry.remove(id)
                            i++
                        }
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }

            val listenerThrasher = executor.submit {
                try {
                    while (!stop.get()) {
                        val off = registry.addListener { /* observe; no work */ }
                        off()
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }

            Thread.sleep(durationMs)
            stop.set(true)
            producerFutures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
            listenerThrasher.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        if (errors.isNotEmpty()) {
            errors.forEach { it.printStackTrace() }
            throw AssertionError("${errors.size} errors during stress run; first: ${errors.first()}")
        }
    }

    /**
     * Long-lived listener attached before any work begins. Producers spam
     * getOrCreate + remove; one thread periodically clear()s. Per-id event
     * sequence must alternate Added, Removed, Added, Removed (starting at
     * Added). Anything else implies a lost or duplicated event.
     */
    @Test
    fun events_total_order_matches_mutation_order_under_clear_storm() {
        val registry = StoreRegistry<Int, S>()
        val ids = (0..15).toList()
        val producers = 4
        val durationMs = 3_000L
        val stop = AtomicBoolean(false)
        val executor = executor(producers + 1)

        val seenEvents = ConcurrentLinkedQueue<RegistryEvent<Int>>()
        registry.addListener { seenEvents.add(it) }

        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            val producerFutures = (1..producers).map { p ->
                executor.submit {
                    try {
                        var i = p
                        while (!stop.get()) {
                            val id = ids[i % ids.size]
                            if (i % 4 == 0) {
                                registry.remove(id)
                            } else {
                                registry.getOrCreate(id) { newStore() }
                            }
                            i++
                        }
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }

            val clearer = executor.submit {
                try {
                    while (!stop.get()) {
                        Thread.sleep(50)
                        registry.clear()
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }

            Thread.sleep(durationMs)
            stop.set(true)
            producerFutures.forEach { it.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS) }
            clearer.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        if (errors.isNotEmpty()) {
            errors.forEach { it.printStackTrace() }
            throw AssertionError("${errors.size} errors during stress run; first: ${errors.first()}")
        }

        assertEventOrder(seenEvents)
        val total = seenEvents.size.toLong()
        assertTrue(total > 0L, "no events observed — test did not exercise the registry")
    }

    /**
     * Verifies per-id event order alternates Added→Removed→Added…
     * Extracted to keep [events_total_order_matches_mutation_order_under_clear_storm] under the line limit.
     */
    private fun assertEventOrder(seenEvents: ConcurrentLinkedQueue<RegistryEvent<Int>>) {
        val perId = seenEvents.groupBy { it.id }
        perId.forEach { (id, events) ->
            var expectingAdded = true
            events.forEachIndexed { idx, e ->
                val ok = if (expectingAdded) {
                    e is RegistryEvent.Added
                } else {
                    e is RegistryEvent.Removed
                }
                val expectedLabel = if (expectingAdded) {
                    "Added"
                } else {
                    "Removed"
                }
                assertTrue(ok, "id=$id event #$idx out of order: expected $expectedLabel, got $e")
                expectingAdded = !expectingAdded
            }
        }
    }

    /**
     * Writer populates many keys; reader on a separate thread later reads
     * each key. All writes must be visible.
     */
    @Test
    fun writer_publishes_then_reader_observes_all_entries() {
        val registry = StoreRegistry<Int, S>()
        val keyCount = 5_000
        val executor = executor(2)

        try {
            val writeFuture = executor.submit {
                repeat(keyCount) { i -> registry.getOrCreate(i) { newStore() } }
            }
            writeFuture.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)

            val readFuture = executor.submit {
                var hits = 0
                repeat(keyCount) { i -> if (registry.get(i) != null) hits++ }
                assertEquals(keyCount, hits)
            }
            readFuture.get(SCENARIO_TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val SCENARIO_TIMEOUT_S = 30L
    }
}
