package org.reduxkotlin.concurrent

/**
 * JVM/Android [DispatchContext]: a per-instance thread-local depth counter, so
 * the flag is correct per thread and per store instance.
 *
 * Uses an anonymous [ThreadLocal] subclass rather than `ThreadLocal.withInitial`,
 * which requires Android API 26 / Java 8 — this module supports API 21.
 */
internal actual class DispatchContext actual constructor() {
    private val depth = object : ThreadLocal<Int>() {
        override fun initialValue(): Int = 0
    }

    actual fun enter() {
        depth.set(depth.get() + 1)
    }

    actual fun exit() {
        val current = depth.get()
        if (current <= 1) depth.remove() else depth.set(current - 1)
    }

    actual val isActive: Boolean
        get() = depth.get() > 0
}
