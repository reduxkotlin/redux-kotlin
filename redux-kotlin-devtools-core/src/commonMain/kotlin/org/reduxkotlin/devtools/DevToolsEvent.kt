package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonElement

/**
 * An event emitted by a [DevToolsSession] on its [DevToolsSession.events] flow. Subscribers
 * ([DevToolsOutput]s) render or stream these. New cases may be added (e.g. pipeline traces in a
 * later module), so consumers must handle the sealed hierarchy exhaustively with an `else` or by
 * ignoring unknown cases.
 */
public sealed interface DevToolsEvent {

    /**
     * The session's initial state, emitted once when the store is created.
     *
     * @property state the serialized preloaded state.
     */
    public data class Initialized(public val state: JsonElement) : DevToolsEvent

    /**
     * A recorded dispatched action and the state it produced.
     *
     * @property actionId monotonic id assigned by the recorder (1-based).
     * @property action the serialized action.
     * @property state the serialized state after the action.
     * @property diff leaf-level changes versus the previous state.
     * @property timestampMillis epoch-millis capture time (dispatch-thread capture, not serialize time).
     * @property isExcess `true` if this action pushed the history past `maxAge` and the oldest was
     *   committed/evicted; the remote wire protocol forwards this flag to the monitor.
     */
    public data class ActionRecorded(
        public val actionId: Int,
        public val action: JsonElement,
        public val state: JsonElement,
        public val diff: List<DiffEntry>,
        public val timestampMillis: Long,
        public val isExcess: Boolean,
    ) : DevToolsEvent
}
