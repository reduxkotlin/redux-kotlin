package org.reduxkotlin.concurrent.concurrency

import org.reduxkotlin.concurrent.DispatchContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies [DispatchContext.isActive] is thread-local: a worker thread entering
 * the context must not make the same instance appear active on another thread.
 * Guards against the JVM actual being accidentally replaced with a plain field.
 */
class DispatchContextThreadIsolationTest {

    @Test
    fun isActive_is_per_thread() {
        val ctx = DispatchContext()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var activeOnWorker = false
        val worker = Thread {
            ctx.enter()
            activeOnWorker = ctx.isActive
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            ctx.exit()
        }.apply { isDaemon = true }
        worker.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS), "worker did not enter")
        // Worker is inside the context; the main thread must NOT observe it active.
        assertFalse(ctx.isActive, "isActive must be thread-local")
        assertTrue(activeOnWorker, "worker should observe its own context active")
        release.countDown()
        worker.join(5_000)
    }
}
