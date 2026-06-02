package org.reduxkotlin.devtools

import kotlinx.serialization.Serializable

/** The role a [PipelineNode] plays in the dispatch pipeline. */
@Serializable
public enum class PipelineNodeKind {
    /** The dispatch entry point (`dispatch(action)`). */
    ENTRY,

    /** A middleware in the chain. */
    MIDDLEWARE,

    /** The root reducer boundary. */
    REDUCER,

    /** A named reducer combined under the root (redux-kotlin combines whole-state reducers). */
    SLICE,
}

/**
 * One node in the static pipeline structure.
 *
 * @property id stable node id used to correlate a [PipelineNodeTrace] to this node.
 * @property label human-readable label (the middleware/reducer name).
 * @property kind the node's role.
 */
@Serializable
public data class PipelineNode(public val id: String, public val label: String, public val kind: PipelineNodeKind)

/**
 * The static structure of a store's dispatch pipeline, registered once. The UI draws this map and
 * lights nodes from a per-action [PipelineTrace].
 *
 * @property nodes ordered nodes: `ENTRY`, then `MIDDLEWARE`s in chain order, then `REDUCER`, then `SLICE`s.
 */
@Serializable
public data class PipelineStructure(public val nodes: List<PipelineNode>)

/**
 * Per-action timing/outcome for a single node.
 *
 * @property nodeId the [PipelineNode.id] this refers to.
 * @property durationNanos wall time spent in the node (monotonic clock).
 * @property forwarded for middleware: whether it called `next` (forwarded the action). `true` for reducers/slices.
 * @property changed for reducers/slices: whether the node produced a new state reference. `false` for middleware.
 */
@Serializable
public data class PipelineNodeTrace(
    public val nodeId: String,
    public val durationNanos: Long,
    public val forwarded: Boolean,
    public val changed: Boolean,
)

/**
 * The trace of one dispatched action through the pipeline.
 *
 * @property actionId the matching [DevToolsEvent.ActionRecorded.actionId]; correlates the trace to its action.
 * @property nodes per-node traces, in traversal order.
 */
@Serializable
public data class PipelineTrace(public val actionId: Int, public val nodes: List<PipelineNodeTrace>)
