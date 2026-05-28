package org.reduxkotlin.concurrent

/**
 * JS [DispatchContext]: single-threaded, so a plain instance counter is correct.
 */
internal actual class DispatchContext actual constructor() {
    private var depth: Int = 0

    actual fun enter() {
        depth++
    }

    actual fun exit() {
        depth--
    }

    actual val isActive: Boolean
        get() = depth > 0
}
