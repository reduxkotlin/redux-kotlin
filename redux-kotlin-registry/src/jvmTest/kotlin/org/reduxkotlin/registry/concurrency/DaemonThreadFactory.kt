package org.reduxkotlin.registry.concurrency

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Marks every worker thread daemon so the JVM can exit even if a leaked
 * worker outlives the test timeout. Numbered names help triage races.
 */
internal class DaemonThreadFactory(private val namePrefix: String = "registry-stress") : ThreadFactory {
    private val counter = AtomicInteger()
    override fun newThread(r: Runnable): Thread = Thread(r, "$namePrefix-${counter.incrementAndGet()}").apply {
        isDaemon = true
    }
}
