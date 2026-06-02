package org.reduxkotlin.devtools

/**
 * Accumulates the current dispatch's [PipelineTrace]. Touched only on the store's dispatch thread
 * during synchronous dispatch; a stack handles nested re-dispatch (middleware dispatching another
 * action) so each action's nodes land on its own trace (LIFO). Not thread-safe by design — dispatch
 * is serialized per store.
 */
internal class PipelineRecorder {
    private val stack = ArrayDeque<MutableList<PipelineNodeTrace>>()

    /** Whether a trace is currently open. */
    val isActive: Boolean get() = stack.isNotEmpty()

    /** Opens a new trace frame for a dispatch. */
    fun begin() {
        stack.addLast(ArrayList())
    }

    /** Appends a node to the current (top) trace frame. No-op if none is open. */
    fun node(nodeId: String, durationNanos: Long, forwarded: Boolean, changed: Boolean) {
        stack.lastOrNull()?.add(PipelineNodeTrace(nodeId, durationNanos, forwarded, changed))
    }

    /** Closes the current frame and returns its trace tagged with [actionId], or `null` if none open. */
    fun commit(actionId: Int): PipelineTrace? {
        val frame = stack.removeLastOrNull() ?: return null
        return PipelineTrace(actionId, frame.toList())
    }
}
