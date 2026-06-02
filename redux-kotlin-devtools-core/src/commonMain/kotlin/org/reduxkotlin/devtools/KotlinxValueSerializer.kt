package org.reduxkotlin.devtools

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * A [ValueSerializer] backed by kotlinx.serialization, for structured cross-platform state/actions.
 *
 * For each value it resolves a serializer from [json]'s `serializersModule` via
 * `getContextual(value::class)` — multiplatform and reflection-free — and encodes it to a
 * [JsonElement]. When no contextual serializer is registered for a value's class, it falls back to
 * [ToStringValueSerializer]. Register your state and (sealed) action base types contextually in
 * [json] for a rich State tree on iOS/native/JS.
 *
 * @param json the configured Json whose `serializersModule` holds the contextual serializers.
 */
public class KotlinxValueSerializer(private val json: Json) : ValueSerializer {
    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    override fun toJson(value: Any?): JsonElement {
        if (value == null) return JsonNull
        val contextual = json.serializersModule.getContextual(value::class) as KSerializer<Any>?
        return if (contextual != null) {
            runCatching { json.encodeToJsonElement(contextual, value) }
                .getOrElse { ToStringValueSerializer.toJson(value) }
        } else {
            ToStringValueSerializer.toJson(value)
        }
    }
}
