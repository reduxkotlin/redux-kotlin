package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class Inc(val by: Int)
private data class Nested(val name: String, val child: Inc, val tags: List<String>)

class ReflectionSerializationTest {
    private val serializer = platformDefaultSerializer()

    @Test
    fun serializesDataClassPropertiesToJsonObject() {
        val json = serializer.toJson(Inc(by = 3))
        assertTrue(json is JsonObject)
        assertEquals(JsonPrimitive(3), json.jsonObject["by"])
    }

    @Test
    fun serializesNestedObjectsAndLists() {
        val json = serializer.toJson(Nested("n", Inc(7), listOf("a", "b"))).jsonObject
        assertEquals(JsonPrimitive("n"), json["name"])
        assertEquals(JsonPrimitive(7), json["child"]!!.jsonObject["by"])
        assertEquals("[\"a\",\"b\"]", json["tags"].toString())
    }

    @Test
    fun fallsBackToStringForNonReflectableValue() {
        assertEquals(JsonPrimitive("plain"), serializer.toJson("plain"))
    }

    @Test
    fun neverThrowsWhenAnElementToStringThrows() {
        val hostile = object {
            override fun toString(): String = throw IllegalStateException("boom")
        }
        // A list containing a value whose reflection/toString may blow up must still yield JSON.
        val result = serializer.toJson(listOf(hostile))
        assertTrue(result is kotlinx.serialization.json.JsonArray || result is JsonPrimitive)
    }
}
