package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

internal actual fun platformDefaultSerializer(): ValueSerializer = ReflectionValueSerializer

private object ReflectionValueSerializer : ValueSerializer {
    @Suppress("TooGenericExceptionCaught") // Serializer contract: must never throw; degrade to string primitive.
    override fun toJson(value: Any?): JsonElement = try {
        convert(value, depth = 0)
    } catch (_: Throwable) {
        JsonPrimitive(runCatching { value?.toString() }.getOrNull() ?: "null")
    }

    private fun convert(value: Any?, depth: Int): JsonElement = when {
        value == null -> JsonNull

        depth > MAX_DEPTH -> JsonPrimitive(value.toString())

        value is Number -> JsonPrimitive(value)

        value is Boolean -> JsonPrimitive(value)

        value is String -> JsonPrimitive(value)

        value is Enum<*> -> JsonPrimitive(value.name)

        value is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to convert(v, depth + 1) },
        )

        value is Iterable<*> -> JsonArray(value.map { convert(it, depth + 1) })

        value is Array<*> -> JsonArray(value.map { convert(it, depth + 1) })

        else -> reflectObject(value, depth)
    }

    private fun reflectObject(value: Any, depth: Int): JsonElement = try {
        val props = value::class.memberProperties
        if (props.isEmpty()) {
            JsonPrimitive(value.toString())
        } else {
            JsonObject(
                props.associate { prop ->
                    prop.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val getter = prop as kotlin.reflect.KProperty1<Any, *>
                    prop.name to convert(runCatching { getter.get(value) }.getOrNull(), depth + 1)
                },
            )
        }
    } catch (_: Throwable) {
        // Serializer must never throw; fall back to toString representation.
        JsonPrimitive(value.toString())
    }

    private const val MAX_DEPTH = 12
}
