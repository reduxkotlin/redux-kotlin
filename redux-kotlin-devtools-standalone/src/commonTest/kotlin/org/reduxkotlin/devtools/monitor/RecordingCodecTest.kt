package org.reduxkotlin.devtools.monitor

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.decodeRecording
import org.reduxkotlin.devtools.bridge.encodeRecording
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingCodecTest {

    @Test
    fun encodes_a_versioned_header_then_one_line_per_message() {
        val msgs = listOf<BridgeMessage>(
            BridgeMessage.Init(buildJsonObject { put("n", 0) }),
            BridgeMessage.Action(
                1,
                buildJsonObject { put("type", "A") },
                buildJsonObject { put("n", 1) },
                emptyList(),
                10L,
                false,
            ),
        )
        val jsonl = encodeRecording(
            RecordingHeader(
                protocolVersion = PROTOCOL_VERSION,
                serializerTier = "toString",
                clientId = "c",
                clientLabel = "C",
                storeName = "S",
                storeInstanceId = "s",
            ),
            msgs,
        )
        val lines = jsonl.trim().split("\n")
        assertEquals(3, lines.size)

        val (header, decoded) = decodeRecording(jsonl)
        assertEquals(PROTOCOL_VERSION, header.protocolVersion)
        assertEquals("S", header.storeName)
        assertEquals(2, decoded.size)
        assertEquals(1, (decoded[1] as BridgeMessage.Action).actionId)
    }
}
