package org.reduxkotlin.concurrent

/**
 * Test fixture: a [NotificationContext] that queues every posted block and runs
 * them only when [drain] is called. Makes async-notify behavior deterministic
 * and single-threaded on every KMP target (including JS/wasm).
 */
internal class QueueingNotificationContext : NotificationContext {
    private val queue = mutableListOf<() -> Unit>()

    /** Number of blocks currently queued and not yet drained. */
    val pending: Int get() = queue.size

    override fun post(block: () -> Unit) {
        queue += block
    }

    /**
     * Runs queued blocks in post order until the queue is empty, including
     * blocks enqueued by the blocks themselves (e.g. nested dispatches).
     */
    fun drain() {
        while (queue.isNotEmpty()) {
            val batch = queue.toList()
            queue.clear()
            batch.forEach { it() }
        }
    }
}
