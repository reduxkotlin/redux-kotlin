package org.reduxkotlin.concurrent.concurrency

import java.util.concurrent.ThreadFactory

/** Marks worker threads daemon so the JVM can exit even if a worker leaks. */
internal class DaemonThreadFactory : ThreadFactory {
    override fun newThread(r: Runnable): Thread = Thread(r).apply { isDaemon = true }
}
