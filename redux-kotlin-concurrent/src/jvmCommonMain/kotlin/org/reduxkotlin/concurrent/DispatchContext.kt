package org.reduxkotlin.concurrent

/**
 * JVM/Android [DispatchContext]: a per-instance [ThreadLocal] depth counter, so
 * the flag is correct per thread and per store instance.
 */
internal actual class DispatchContext actual constructor() {
    private val depth = ThreadLocal.withInitial { 0 }

    actual fun enter() {
        depth.set(depth.get() + 1)
    }

    actual fun exit() {
        depth.set(depth.get() - 1)
    }

    actual val isActive: Boolean
        get() = depth.get() > 0
}
