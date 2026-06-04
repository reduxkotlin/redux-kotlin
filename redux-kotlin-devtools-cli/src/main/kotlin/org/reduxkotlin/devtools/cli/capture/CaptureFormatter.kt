package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DiffEntry
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.bridgeJson

/** Output granularity tiers; each is a strict superset of the previous. */
internal enum class Format { ACTIONS, DIFF, FULL }

private val prettyJson = Json(bridgeJson) { prettyPrint = true }

/** Render one action record to a single JSON object string at the requested [format] tier. */
internal fun formatRecord(
    action: BridgeMessage.Action,
    format: Format,
    store: String,
    pretty: Boolean = false,
): String {
    val obj: JsonObject = buildJsonObject {
        put("actionId", JsonPrimitive(action.actionId))
        put("type", JsonPrimitive(actionType(action.action)))
        put("store", JsonPrimitive(store))
        put("ts", JsonPrimitive(action.timestampMillis))
        if (action.isExcess) put("isExcess", JsonPrimitive(true))
        if (format == Format.DIFF || format == Format.FULL) {
            put("diff", bridgeJson.encodeToJsonElement(ListSerializer(DiffEntry.serializer()), action.diff))
        }
        if (format == Format.FULL) put("state", action.state)
    }
    val j = if (pretty) prettyJson else bridgeJson
    return j.encodeToString(JsonObject.serializer(), obj)
}
