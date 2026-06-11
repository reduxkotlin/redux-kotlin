package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.encodeRecording
import java.io.File
import java.nio.file.Files
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

    private fun tempCapture(text: String): File =
        Files.createTempDirectory("rkread").toFile().resolve("cap.jsonl").apply { writeText(text) }

    @Test
    fun reads_actions_and_tolerates_trailing_partial_line() {
        val text = encodeRecording(header(), listOf(action(1, "A"), action(2, "B"))) + "{\"t\":\"acti" // partial
        val (h, actions) = readCapture(tempCapture(text))
        assertEquals("TaskFlow", h.storeName)
        assertEquals(listOf(1, 2), actions.map { it.actionId })
        assertEquals(listOf("A", "B"), actions.map { actionType(it.action) })
    }

    @Test
    fun non_action_messages_are_filtered_out() {
        val msgs = listOf(BridgeMessage.Init(buildJsonObject { put("n", 0) }), action(1, "A"))
        val (_, actions) = readCapture(tempCapture(encodeRecording(header(), msgs)))
        assertEquals(listOf(1), actions.map { it.actionId })
    }
}
