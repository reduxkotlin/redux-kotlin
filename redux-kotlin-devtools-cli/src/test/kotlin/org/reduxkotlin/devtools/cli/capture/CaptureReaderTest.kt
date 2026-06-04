package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.encodeRecording
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CaptureReaderTest {
    private fun header() = RecordingHeader(
        protocolVersion = PROTOCOL_VERSION,
        serializerTier = "json",
        clientId = "taskflow",
        clientLabel = "TaskFlow",
        storeName = "TaskFlow",
        storeInstanceId = "root",
    )

    private fun action(id: Int, type: String) = BridgeMessage.Action(
        actionId = id,
        action = buildJsonObject { put("type", JsonPrimitive(type)) },
        state = buildJsonObject { put("n", JsonPrimitive(id)) },
        diff = emptyList(),
        timestampMillis = id.toLong(),
        isExcess = false,
    )

    @Test
    fun reads_actions_and_tolerates_trailing_partial_line() {
        val text = encodeRecording(header(), listOf(action(1, "A"), action(2, "B"))) + "{\"t\":\"acti" // partial
        val (h, actions) = parseCapture(text)
        assertEquals("TaskFlow", h.storeName)
        assertEquals(listOf(1, 2), actions.map { it.actionId })
        assertEquals(listOf("A", "B"), actions.map { actionType(it.action) })
    }
}
