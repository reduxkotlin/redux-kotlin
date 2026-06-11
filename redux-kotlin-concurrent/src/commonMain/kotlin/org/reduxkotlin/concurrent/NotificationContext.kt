package org.reduxkotlin.concurrent

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
 * in post order, with a happens-before edge between consecutive blocks (any
 * single-threaded executor, main-thread post, or inline execution qualifies).
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
 * A [NotificationContext] that runs the callback **inline** when already on the target thread, and
 * otherwise hands it to [post] for marshaling.
 *
 * Avoids the read-after-dispatch lag a purely-posting context introduces: when a UI thread dispatches
 * and a subscriber updates UI state, an always-posting context (e.g. a bare `Handler.post`) delivers
 * the callback on a *later* loop iteration, so observers can briefly read stale state. Running inline
 * on the target thread keeps the subscriber synchronous with the dispatch (matching a plain
 * synchronous store), while off-thread dispatches still marshal via [post] (preserving the
 * off-main-effects rule).
 *
 * "Coalescing" refers to this inline-vs-marshal routing only — bursts are NOT collapsed: every
 * dispatch still delivers exactly one callback per subscriber.
 *
 * @param isOnTargetThread returns true when the calling thread is the target (e.g. the main thread).
 * @param post marshals [block] to the target thread (e.g. `handler::post`); used only when
 *   [isOnTargetThread] returns false.
 * @return a [NotificationContext] that coalesces to inline execution on the target thread.
 */
public fun coalescingNotificationContext(
    isOnTargetThread: () -> Boolean,
    post: (block: () -> Unit) -> Unit,
): NotificationContext = NotificationContext { block ->
    if (isOnTargetThread()) block() else post(block)
}

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
