package org.reduxkotlin.devtools.inapp.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace
import org.reduxkotlin.devtools.inapp.DevToolsTab

/** A registered output row in the Outputs tab. */
public data class OutputRow(
    /** Stable output id (e.g. `"inapp"`, `"remote"`). */
    public val id: String,
    /** Display label. */
    public val label: String,
    /** Whether the output is currently enabled. */
    public val enabled: Boolean,
    /** `true` for the in-app output, which cannot be toggled off. */
    public val locked: Boolean,
)

/**
 * Immutable UI state for the drawer. Produced by [InAppModel] from the session event stream.
 *
 * @property actions recorded actions, oldest first (bounded by `maxAge`).
 * @property selectedId the action currently selected (drives State/Diff/Pipeline); defaults to the newest.
 * @property filter case-insensitive substring filter on action type.
 * @property activeTab the visible tab.
 * @property structure the static pipeline structure, if registered.
 * @property tracesById per-action pipeline traces, keyed by action id.
 * @property outputs the registered outputs and their on/off state.
 */
public data class InAppState(
    public val actions: List<DevToolsEvent.ActionRecorded> = emptyList(),
    public val selectedId: Int? = null,
    public val filter: String = "",
    public val activeTab: DevToolsTab = DevToolsTab.ACTIONS,
    public val structure: PipelineStructure? = null,
    public val tracesById: Map<Int, PipelineTrace> = emptyMap(),
    public val outputs: List<OutputRow> = emptyList(),
) {
    /** Actions matching [filter] (all when blank). */
    public val filteredActions: List<DevToolsEvent.ActionRecorded>
        get() = if (filter.isBlank()) {
            actions
        } else {
            actions.filter {
                actionType(
                    it.action,
                ).contains(filter, ignoreCase = true)
            }
        }

    /** The selected action, or the newest, or `null` if empty. */
    public val selected: DevToolsEvent.ActionRecorded?
        get() = actions.firstOrNull { it.actionId == selectedId } ?: actions.lastOrNull()
}

/** Best-effort action type label from a serialized action (`type` field, else the primitive/string form). */
public fun actionType(action: JsonElement): String = when (action) {
    is JsonObject -> (action["type"] as? JsonPrimitive)?.content
        ?: (action["__name"] as? JsonPrimitive)?.content
        ?: "action"

    is JsonPrimitive -> action.content

    else -> action.toString()
}
