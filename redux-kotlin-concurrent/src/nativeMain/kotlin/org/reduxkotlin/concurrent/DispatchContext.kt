package org.reduxkotlin.concurrent

import kotlin.native.concurrent.ThreadLocal

/**
 * Per-thread depth maps, one entry per thread (each thread gets its own
 * [perThreadDepth] instance via [ThreadLocal]). Keyed by store-context identity
 * so two stores nesting on the same thread stay independent.
 */
@ThreadLocal
private val perThreadDepth: MutableMap<DispatchContext, Int> = mutableMapOf()

/**
 * Native [DispatchContext]: a thread-local map keyed by this instance. The new
 * Kotlin/Native memory manager gives each thread its own [perThreadDepth].
 */
internal actual class DispatchContext actual constructor() {
    actual fun enter() {
        perThreadDepth[this] = (perThreadDepth[this] ?: 0) + 1
    }

    actual fun exit() {
        val next = (perThreadDepth[this] ?: 0) - 1
        if (next <= 0) {
            perThreadDepth.remove(this)
        } else {
            perThreadDepth[this] = next
        }
    }

    actual val isActive: Boolean
        get() = (perThreadDepth[this] ?: 0) > 0
}
