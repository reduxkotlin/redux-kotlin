package org.reduxkotlin.devtools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinxValueSerializerTest {

    @Serializable
    private data class St(val n: Int, val label: String)

    private val json = Json {
        serializersModule = SerializersModule { contextual(St::class, St.serializer()) }
    }

    @Test
    fun serializes_a_registered_type_structurally() {
        val ser = KotlinxValueSerializer(json)
        val el = ser.toJson(St(n = 3, label = "hi"))
        assertTrue(el is JsonObject)
        assertEquals(JsonPrimitive(3), (el as JsonObject)["n"])
        assertEquals(JsonPrimitive("hi"), el["label"])
    }

    @Test
    fun falls_back_to_toString_for_unregistered_types() {
        val ser = KotlinxValueSerializer(json)
        val el = ser.toJson(42)
        assertEquals(JsonPrimitive("42"), el)
    }

    @Test
    fun null_serializes_to_json_null() {
        val ser = KotlinxValueSerializer(json)
        assertEquals(kotlinx.serialization.json.JsonNull, ser.toJson(null))
    }
}
