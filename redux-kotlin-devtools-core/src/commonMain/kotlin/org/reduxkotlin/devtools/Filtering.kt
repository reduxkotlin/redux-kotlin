package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Returns `true` if [action] should be recorded given the [denylist]/[allowlist] regexes. An action
 * is identified by its class `simpleName` (falling back to `toString()`); denied matches win, and a
 * non-empty allowlist must match.
 */
internal fun shouldSend(action: Any, denylist: List<Regex>, allowlist: List<Regex>): Boolean {
    val key = action::class.simpleName ?: action.toString()
    val denied = denylist.any { it.containsMatchIn(key) }
    val allowed = allowlist.isEmpty() || allowlist.any { it.containsMatchIn(key) }
    return !denied && allowed
}

/**
 * Serializes [action] into a [JsonObject] carrying a `type` field that mirrors the JS Redux
 * convention `{ type, ...payload }` used by the DevTools monitor to label each action entry.
 *
 * The class [simpleName][kotlin.reflect.KClass.simpleName] is injected as `type`. An existing
 * non-empty string `type` field is respected as-is. Field-less objects or non-object serialization
 * results are wrapped as `{ "type": <name>, "value": <serialized> }`.
 */
internal fun ValueSerializer.toActionJson(action: Any): JsonObject {
    val typeName = action::class.simpleName ?: action.toString()
    return when (val json = toJson(action)) {
        is JsonObject -> {
            val existing = json["type"]
            if (existing is JsonPrimitive && existing.isString && existing.content.isNotEmpty()) {
                json
            } else {
                buildJsonObject {
                    put("type", JsonPrimitive(typeName))
                    json.forEach { (key, value) -> if (key != "type") put(key, value) }
                }
            }
        }

        JsonNull -> JsonObject(mapOf("type" to JsonPrimitive(typeName)))

        else -> buildJsonObject {
            put("type", JsonPrimitive(typeName))
            put("value", json)
        }
    }
}
