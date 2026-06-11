// Mirrors core's KotlinxValueSerializer.kt — same constructor signature so the documented
// `DevToolsConfig(serializer = KotlinxValueSerializer(json))` integration compiles unchanged when
// the release classpath swaps core for this no-op.
package org.reduxkotlin.devtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * No-op mirror of core's `KotlinxValueSerializer`. In release builds nothing records, so this never
 * serializes anything structurally; if it is ever invoked it degrades to [ToStringValueSerializer].
 *
 * @param json accepted for signature parity with the debug artifact; unused here.
 */
@Suppress("UnusedParameter")
public class KotlinxValueSerializer(json: Json) : ValueSerializer {
    override fun toJson(value: Any?): JsonElement = ToStringValueSerializer.toJson(value)
}
