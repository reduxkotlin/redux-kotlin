package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts an arbitrary action or state value into a [JsonElement] for the DevTools wire format.
 * Implementations must never throw — degrade to a string instead.
 */
public interface ValueSerializer {
    /** Serializes [value] to a [JsonElement]; returns a string primitive on failure. */
    public fun toJson(value: Any?): JsonElement
}

/** A [ValueSerializer] that renders any value as its `toString()` — the universal fallback tier. */
public object ToStringValueSerializer : ValueSerializer {
    override fun toJson(value: Any?): JsonElement =
        JsonPrimitive(runCatching { value?.toString() }.getOrNull() ?: "null")
}

/**
 * The zero-config default serializer for the current platform: reflection-based structured JSON
 * on JVM/Android, and [ToStringValueSerializer] elsewhere.
 */
internal expect fun platformDefaultSerializer(): ValueSerializer
