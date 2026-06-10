package org.reduxkotlin.concurrent

/**
 * Decides on which thread/dispatcher a [ConcurrentStore]'s listener callbacks
 * (and the store's `onError` handler) are invoked.
 *
 * The default [Inline] runs the callback synchronously on the dispatching
 * thread, which preserves the store's publish-after-notify read ordering. UI
 * consumers should supply a context that marshals to the main thread (e.g. a
 * wrapper around the platform main dispatcher), so subscribers that touch
 * UI state never run off-main.
 *
 * Note: a non-[Inline] (asynchronous) context relaxes the no-mid-listener-tear
 * guarantee to eventual consistency — the mirror is published when the reducer
 * completes, while listeners arrive later on the target dispatcher.
 */
public fun interface NotificationContext {
    /**
     * Invokes [block] on this context. [Inline] runs it immediately; other
     * implementations may post it to another thread/dispatcher.
     */
    public fun post(block: () -> Unit)

    /** Built-in contexts. */
    public companion object {
        /** Runs the block synchronously on the calling (dispatching) thread. */
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
 */
public val LogAndContinue: (Throwable) -> Unit = { throwable ->
    println("redux-kotlin-concurrent: listener threw and was isolated: $throwable")
}
