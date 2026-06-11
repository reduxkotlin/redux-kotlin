package org.reduxkotlin.devtools.bridge

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordingTest {

    private val header = RecordingHeader(
        protocolVersion = PROTOCOL_VERSION,
        serializerTier = "toString",
        clientId = "client-1",
        clientLabel = "test app",
        storeName = "store",
        storeInstanceId = "store-1",
    )

    private val messages = listOf<BridgeMessage>(
        BridgeMessage.Init(JsonObject(mapOf("count" to JsonPrimitive(0)))),
        BridgeMessage.Action(
            actionId = 1,
            action = JsonObject(mapOf("type" to JsonPrimitive("Inc"))),
            state = JsonObject(mapOf("count" to JsonPrimitive(1))),
            diff = emptyList(),
            timestampMillis = 42L,
            isExcess = false,
        ),
    )

    @Test
    fun encode_then_decode_roundtrips_header_and_messages() {
        val text = encodeRecording(header, messages)
        val (decodedHeader, decodedMessages) = decodeRecording(text)
        assertEquals(header, decodedHeader)
        assertEquals(messages, decodedMessages)
    }

    @Test
    fun decode_ignores_blank_lines() {
        val text = "\n" + encodeRecording(header, messages).replace("\n", "\n\n")
        val (decodedHeader, decodedMessages) = decodeRecording(text)
        assertEquals(header, decodedHeader)
        assertEquals(messages, decodedMessages)
    }

    @Test
    fun decoding_an_empty_recording_fails() {
        assertFailsWith<IllegalArgumentException> { decodeRecording("  \n \n") }
    }
}
