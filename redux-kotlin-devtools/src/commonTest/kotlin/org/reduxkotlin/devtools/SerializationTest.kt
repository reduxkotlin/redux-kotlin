package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {
    @Test
    fun toStringSerializerWrapsValueAsString() {
        val serializer = ToStringValueSerializer
        val result = serializer.toJson("hello")
        assertEquals(JsonPrimitive("hello"), result)
    }

    @Test
    fun defaultSerializerNeverThrowsOnArbitraryObject() {
        // platformDefaultSerializer must always return *some* JsonElement
        val result = platformDefaultSerializer().toJson(object {
            override fun toString() = "x"
        })
        assertEquals(JsonPrimitive("x"), result)
    }
}
