package org.reduxkotlin.devtools.cli.server

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.cli.capture.readCapture
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CaptureWriterTest {
    @Test
    fun writes_a_gui_loadable_recording_for_a_store() {
        val dir = Files.createTempDirectory("rkcap").toFile()
        val header = RecordingHeader(
            protocolVersion = PROTOCOL_VERSION,
            serializerTier = "json",
            clientId = "taskflow",
            clientLabel = "TaskFlow",
            storeName = "TaskFlow",
            storeInstanceId = "root",
        )
        val msgs = listOf(
            BridgeMessage.Action(
                1,
                buildJsonObject { put("type", JsonPrimitive("A")) },
                buildJsonObject {},
                emptyList(),
                1L,
                false,
            ),
        )
        val file: File = writeStoreCapture(dir, "taskflow::root", header, msgs)
        assertTrue(file.name.endsWith(".jsonl"))
        assertEquals(listOf(1), readCapture(file).second.map { it.actionId })
    }
}
