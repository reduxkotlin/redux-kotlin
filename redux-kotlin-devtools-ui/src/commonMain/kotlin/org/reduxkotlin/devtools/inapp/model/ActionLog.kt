package org.reduxkotlin.devtools.inapp.model

import kotlinx.serialization.json.JsonObject
import org.reduxkotlin.devtools.DevToolsEvent

/**
 * A flattened, store-tagged action-log row. One renderer (single-store or merged "All stores")
 * consumes these: each row carries the store it came from so the merged view can show a per-row
 * store badge while the single-store view omits it.
 *
 * @property storeId the id of the store the action came from.
 * @property storeName the store's display name (shown as a badge in merged mode).
 * @property merged `true` when produced for the merged ("All stores") view; drives badge visibility.
 * @property event the recorded action this row represents.
 */
public data class ActionLogRow(
    public val storeId: String,
    public val storeName: String,
    public val merged: Boolean,
    public val event: DevToolsEvent.ActionRecorded,
)

/** Best-effort payload preview from an action's JSON object (keys other than `type`/`__name`). */
public fun payloadPreview(event: DevToolsEvent.ActionRecorded): String {
    val obj = event.action as? JsonObject ?: return ""
    val entries = obj.filterKeys { it != "type" && it != "__name" }
    return if (entries.isEmpty()) "" else "{ " + entries.entries.joinToString(", ") { "${it.key}: ${it.value}" } + " }"
}

/**
 * Whether this row matches the search [query]. A blank query matches everything. The haystack spans
 * the action id, type, payload preview, serialized state and store name. When [regex] is `true` the
 * query is compiled case-insensitively (a malformed pattern matches nothing); otherwise it is a
 * case-insensitive substring test.
 */
public fun ActionLogRow.matches(query: String, regex: Boolean): Boolean {
    if (query.isBlank()) return true
    val hay = "${event.actionId} ${actionType(event.action)} ${payloadPreview(event)} ${event.state} $storeName"
    return if (regex) {
        runCatching { Regex(query, RegexOption.IGNORE_CASE).containsMatchIn(hay) }.getOrDefault(false)
    } else {
        hay.contains(query, ignoreCase = true)
    }
}

/**
 * Flattens this registry snapshot into action-log rows. In merged mode (`merged == true`) it returns
 * every selected store's actions interleaved by time, each tagged merged. Otherwise it resolves the
 * active store — preferring [activeStoreId] (if still selected), else the first selected store, else
 * the first registered store — and maps its actions to single-store rows (empty when there are none).
 */
public fun StoreRegistryState.actionLogRows(activeStoreId: String?): List<ActionLogRow> = if (merged) {
    mergedRows.map { ActionLogRow(it.storeId, it.storeName, true, it.event) }
} else {
    val store = stores.firstOrNull { it.ref.id == activeStoreId && it.ref.id in selectedIds }
        ?: stores.firstOrNull { it.ref.id in selectedIds }
        ?: stores.firstOrNull()
    store?.state?.actions?.map { ActionLogRow(store.ref.id, store.ref.name, false, it) } ?: emptyList()
}

/**
 * Flattens a single-session overlay's state (no registry) into single-store action-log rows tagged
 * with [storeId] / [storeName].
 */
public fun InAppState.actionLogRows(storeId: String, storeName: String): List<ActionLogRow> =
    actions.map { ActionLogRow(storeId, storeName, false, it) }
