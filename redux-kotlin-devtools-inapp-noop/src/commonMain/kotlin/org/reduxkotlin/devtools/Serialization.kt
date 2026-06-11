// Mirrors core's Serialization.kt — file name kept identical so the JVM file facade matches the
// debug artifact's.
package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * No-op mirror of core's [ValueSerializer] (release builds never call it).
 * Present so that integrators can reference this type in shared/main code
 * without a compile error when the release classpath swaps core for the no-op.
 */
public interface ValueSerializer {
    /** Serializes [value] to a [JsonElement]; returns a string primitive on failure. */
    public fun toJson(value: Any?): JsonElement
}

/** No-op mirror of core's [ToStringValueSerializer]. */
public object ToStringValueSerializer : ValueSerializer {
    override fun toJson(value: Any?): JsonElement =
        JsonPrimitive(runCatching { value?.toString() }.getOrNull() ?: "null")
}
