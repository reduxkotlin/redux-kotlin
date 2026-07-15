package org.reduxkotlin.concurrent.concurrency

import org.reduxkotlin.concurrent.coalescingNotificationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the serial drain claim with real concurrent producers. */
class CoalescingNotificationContextConcurrencyTest {

    @Test
    fun concurrentProducersRunOnlyOneCallbackAtATime() {
        val producerCount = 7
        val callbacksPerProducer = 10
        val callbackCount = 1 + producerCount * callbacksPerProducer
        val executor = Executors.newFixedThreadPool(4, DaemonThreadFactory())
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val delivered = CountDownLatch(callbackCount)
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val context = coalescingNotificationContext(
            isOnTargetThread = { false },
            post = { callback -> executor.execute(callback) },
        )

        fun callback(waitForRelease: Boolean = false): () -> Unit = {
            val current = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, current) }
            try {
                if (waitForRelease) {
                    firstEntered.countDown()
                    check(releaseFirst.await(AWAIT_SECONDS, TimeUnit.SECONDS)) { "first callback was not released" }
                }
            } finally {
                active.decrementAndGet()
                delivered.countDown()
            }
        }

        try {
            context.post(callback(waitForRelease = true))
            assertTrue(firstEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "first callback did not start")
            val producers = (0 until producerCount).map { index ->
                thread(name = "notification-producer-$index", isDaemon = true) {
                    repeat(callbacksPerProducer) { context.post(callback()) }
                }
            }
            producers.forEach { it.join(TimeUnit.SECONDS.toMillis(AWAIT_SECONDS)) }
            releaseFirst.countDown()

            assertTrue(delivered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "callbacks were not delivered")
            assertEquals(1, maxActive.get())
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private companion object {
        private const val AWAIT_SECONDS: Long = 10
    }
}
