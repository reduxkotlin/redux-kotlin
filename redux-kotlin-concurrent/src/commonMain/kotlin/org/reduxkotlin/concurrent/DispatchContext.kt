package org.reduxkotlin.concurrent

/**
 * Per-store, per-thread "am I currently inside this store's dispatch?" flag.
 *
 * Used to route `getState`: while [isActive] is true for the calling thread,
 * reads go to the inner store (the in-progress state, matching core Redux
 * semantics for listeners); otherwise reads hit the lock-free state mirror.
 *
 * [enter]/[exit] are reentrant (depth-counted) so nested dispatch keeps the
 * context active until the outermost dispatch unwinds. JVM/Native back this
 * with thread-local storage; JS/wasm are single-threaded and use a plain
 * counter.
 */
internal expect class DispatchContext() {
    /** Marks the current thread as inside this store's dispatch. */
    fun enter()

    /** Balances a prior [enter] for the current thread. */
    fun exit()

    /** True while the current thread is between [enter] and its balancing [exit]. */
    val isActive: Boolean
}
