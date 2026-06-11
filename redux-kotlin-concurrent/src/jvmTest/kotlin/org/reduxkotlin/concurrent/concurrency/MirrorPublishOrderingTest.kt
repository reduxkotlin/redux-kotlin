package org.reduxkotlin.concurrent.concurrency

import org.reduxkotlin.concurrent.CallerSerializedStore
import org.reduxkotlin.concurrent.LogAndContinue
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.granular.subscribeTo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the publish-then-signal contract (hardening plan C1): a posted subscriber
 * callback must observe state at least as new as the dispatch that triggered it.
 *
 * The pre-fix code published the mirror in `sequenced()`'s finally, AFTER the
 * fan-out posts — so a posted callback racing ahead of the dispatching thread
 * read the stale mirror, the granular diff saw no change, and no further
 * callback ever arrived for that dispatch (lost wakeup; the binding stayed
 * stale until the next dispatch).
 *
 * The race is frozen deterministically: a second listener registered directly
 * on the INNER store parks the dispatching thread after the wrapper's fan-out
 * hook has posted (the hook subscribes first, in `init`) but before the
 * pre-fix finally-publish could run. The posted callback then provably executes
 * against whatever mirror is published at fan-out time.
 */
class MirrorPublishOrderingTest {

    private data class S(val count: Int = 0)
    private object Inc

    private val awaitSeconds = 30L

    @Test
    fun postedCallbackObservesStateAtLeastAsNewAsItsTriggeringDispatch() {
        val executor = Executors.newSingleThreadExecutor(DaemonThreadFactory())
        try {
            val postedDone = CountDownLatch(1)
            // Posting context backed by a single-thread executor: callbacks run
            // off the dispatching thread, like a main-thread Handler would.
            val postingContext = NotificationContext { block ->
                executor.execute {
                    block()
                    postedDone.countDown()
                }
            }
            val store = CallerSerializedStore(
                inner = org.reduxkotlin.createStore(
                    { s: S, a -> if (a is Inc) s.copy(count = s.count + 1) else s },
                    S(),
                ),
                notificationContext = postingContext,
                onError = LogAndContinue,
            )

            // Diff-based consumer (the layer the race actually bit): fires only
            // when the value it reads at callback time differs from its last.
            val observed = mutableListOf<Int>()
            store.subscribeTo({ it.count }, triggerOnSubscribe = false) { _, new ->
                observed += new
            }

            // Parks the dispatching thread inside the inner notification loop,
            // AFTER the wrapper's fan-out hook (registered first in init) has
            // posted, and BEFORE sequenced()'s finally can run.
            val release = CountDownLatch(1)
            store.store.subscribe {
                check(release.await(awaitSeconds, TimeUnit.SECONDS)) { "release latch timed out" }
            }

            val dispatcher = thread(isDaemon = true) { store.dispatch(Inc) }

            // The posted granular callback runs to completion while the
            // dispatching thread is still parked pre-(finally-)publish.
            assertTrue(postedDone.await(awaitSeconds, TimeUnit.SECONDS), "posted callback never ran")
            release.countDown()
            dispatcher.join(TimeUnit.SECONDS.toMillis(awaitSeconds))

            // Drain the executor so any (correct or buggy) trailing work lands.
            val drained = CountDownLatch(1)
            executor.execute { drained.countDown() }
            assertTrue(drained.await(awaitSeconds, TimeUnit.SECONDS), "executor never drained")

            assertEquals(
                listOf(1),
                observed,
                "posted callback must observe the state of its triggering dispatch exactly once " +
                    "(empty = lost wakeup: callback read the pre-publish mirror)",
            )
            assertEquals(1, store.state.count)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun stormNeverLosesTheFinalValue() {
        val executor = Executors.newSingleThreadExecutor(DaemonThreadFactory())
        try {
            val postingContext = NotificationContext { block -> executor.execute(block) }
            val store = CallerSerializedStore(
                inner = org.reduxkotlin.createStore(
                    { s: S, a -> if (a is Inc) s.copy(count = s.count + 1) else s },
                    S(),
                ),
                notificationContext = postingContext,
                onError = LogAndContinue,
            )

            val lastObserved = java.util.concurrent.atomic.AtomicInteger(-1)
            store.subscribeTo({ it.count }, triggerOnSubscribe = false) { _, new ->
                lastObserved.set(new)
            }

            val dispatchesPerThread = 1000
            val threads = (1..2).map {
                thread(isDaemon = true) {
                    repeat(dispatchesPerThread) { store.dispatch(Inc) }
                }
            }
            threads.forEach { it.join(TimeUnit.SECONDS.toMillis(awaitSeconds)) }

            // Quiesce the notification executor, then the diff layer must have
            // observed the final value (no lost trailing wakeup).
            val drained = CountDownLatch(1)
            executor.execute { drained.countDown() }
            assertTrue(drained.await(awaitSeconds, TimeUnit.SECONDS), "executor never drained")

            assertEquals(2 * dispatchesPerThread, store.state.count)
            assertEquals(
                2 * dispatchesPerThread,
                lastObserved.get(),
                "diff layer missed the final dispatch (lost trailing wakeup)",
            )
        } finally {
            executor.shutdownNow()
        }
    }
}
