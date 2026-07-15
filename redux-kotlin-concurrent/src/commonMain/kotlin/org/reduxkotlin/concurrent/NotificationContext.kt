package org.reduxkotlin.concurrent

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Decides on which thread/dispatcher a [ConcurrentStore]'s listener callbacks
 * (and the store's `onError` handler) are invoked.
 *
 * The store publishes its read mirror **before** signaling listeners, so a
 * callback — inline or posted — always observes state at least as new as the
 * dispatch that triggered it. Callbacks must pull current state via
 * `getState`; a notification is a signal that something may have changed,
 * never a payload (later dispatches may already have landed).
 *
 * Implementations MUST execute posted blocks for a given store one at a time,
 * in FIFO order at the [post] synchronization point, with a happens-before
 * edge between consecutive blocks. Concurrent callers have no external total
 * order beyond that point. A target-thread implementation may run a block
 * inline only when no earlier block is queued or draining; a block posted
 * reentrantly runs after the current block returns. Any single-threaded
 * executor, main-thread post, or inline execution qualifies.
 * Handing blocks to a multi-threaded executor is unsupported: diff-based
 * consumers (redux-kotlin-granular's per-entry last value, and therefore
 * `selectorState`/`fieldState`) assume serial notification and will lose or
 * duplicate diffs otherwise.
 *
 * The default [Inline] runs every callback synchronously on the dispatching
 * thread while the writer lock is held — a slow subscriber delays concurrent
 * dispatchers (and `replaceReducer`), never readers. UI consumers should
 * supply a context that marshals to the main thread (e.g.
 * [coalescingNotificationContext] around the platform main dispatcher), so
 * subscribers that touch UI state never run off-main.
 */
public fun interface NotificationContext {
    /**
     * Invokes [block] on this context. [Inline] runs it immediately; other
     * implementations may post it to another thread/dispatcher.
     */
    public fun post(block: () -> Unit)

    /** Built-in contexts. */
    public companion object {
        /**
         * Runs the block synchronously on the calling (dispatching) thread,
         * while the writer lock is still held — slow subscribers delay other
         * dispatchers, never readers. Supply a posting or coalescing context
         * if subscribers can be slow.
         */
        public val Inline: NotificationContext = NotificationContext { block -> block() }
    }
}

/**
 * A [NotificationContext] that serializes callbacks onto the target thread.
 * An idle target-thread post runs inline; all other posts join a FIFO queue.
 *
 * Avoids the read-after-dispatch lag a purely-posting context introduces: when a UI thread dispatches
 * and a subscriber updates UI state, an always-posting context (e.g. a bare `Handler.post`) delivers
 * the callback on a *later* loop iteration, so observers can briefly read stale state. Running inline
 * on the target thread keeps the subscriber synchronous with the dispatch (matching a plain
 * synchronous store), while off-thread dispatches still marshal via [post] (preserving the
 * off-main-effects rule).
 *
 * "Coalescing" means a burst has at most one active scheduled drain; it never
 * collapses callbacks. Every dispatch still delivers exactly one callback per
 * subscriber. A drain processes a bounded batch before posting a continuation,
 * letting sustained notification traffic yield to the target event loop.
 *
 * @param isOnTargetThread returns true when the calling thread is the target (e.g. the main thread).
 * @param post asynchronously marshals [block] to the target thread (e.g.
 *   `handler::post`). It must either accept the block or throw; adapters with a
 *   rejection result must throw when rejected. It is never called while the
 *   context lock is held.
 * @return a serial [NotificationContext] that keeps the idle target-thread
 *   fast path without allowing it to overtake queued work.
 */
public fun coalescingNotificationContext(
    isOnTargetThread: () -> Boolean,
    post: (block: () -> Unit) -> Unit,
): NotificationContext = CoalescingNotificationContext(isOnTargetThread, post)

private class CoalescingNotificationContext(
    private val isOnTargetThread: () -> Boolean,
    private val postToTarget: (block: () -> Unit) -> Unit,
) : NotificationContext {
    private val lock = SynchronizedObject()
    private val queue = ArrayDeque<() -> Unit>()
    private var draining = false
    private val drain: () -> Unit = ::drain

    private fun releaseDrainClaim() {
        synchronized(lock) { draining = false }
    }

    private fun scheduleDrain() {
        try {
            postToTarget(drain)
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
            // Keep queued callbacks retryable when the target loop is shutting down.
            releaseDrainClaim()
            throw throwable
        }
    }

    private fun drain() {
        var firstFailure: Throwable? = null
        var delivered = 0
        while (delivered < MAX_CALLBACKS_PER_DRAIN) {
            val callback = synchronized(lock) { queue.removeFirstOrNull() } ?: break
            try {
                callback()
            } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
                if (firstFailure == null) firstFailure = throwable
            }
            delivered++
        }

        val hasMore = synchronized(lock) {
            if (queue.isEmpty()) {
                draining = false
                false
            } else {
                true
            }
        }
        if (hasMore) scheduleDrain()
        firstFailure?.let { throw it }
    }

    override fun post(block: () -> Unit) {
        val startDrain = synchronized(lock) {
            queue.addLast(block)
            if (draining) {
                false
            } else {
                draining = true
                true
            }
        }
        if (startDrain) {
            try {
                if (isOnTargetThread()) drain() else scheduleDrain()
            } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
                // isOnTargetThread may fail too; no failure may retain the claim.
                releaseDrainClaim()
                throw throwable
            }
        }
    }
}

private const val MAX_CALLBACKS_PER_DRAIN: Int = 64

/**
 * Default `onError` handler for [createConcurrentStore]: prints the throwable and
 * continues, so one failing listener never aborts delivery to the others or
 * corrupts the store. Override to forward to your own logging.
 *
 * A handler must not throw; if it does, the throwable is printed and swallowed —
 * dispatch and delivery to the remaining subscribers always proceed.
 */
public val LogAndContinue: (Throwable) -> Unit = { throwable ->
    println("redux-kotlin-concurrent: listener threw and was isolated: $throwable")
}
