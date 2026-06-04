package org.reduxkotlin.devtools.cli

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.cli.capture.Format
import org.reduxkotlin.devtools.cli.capture.actionType
import org.reduxkotlin.devtools.cli.capture.formatRecord
import org.reduxkotlin.devtools.cli.capture.readCapture
import org.reduxkotlin.devtools.cli.command.resolveStore
import org.reduxkotlin.devtools.cli.server.flushAll
import org.reduxkotlin.devtools.monitor.MonitorIngest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class EndToEndTest {
    @Test
    fun ingest_to_capture_to_query() {
        val dir = Files.createTempDirectory("rke2e").toFile()
        val ingest = MonitorIngest()
        val conn = ingest.openConnection()
        conn.accept(
            BridgeMessage.Hello(
                protocolVersion = PROTOCOL_VERSION,
                clientId = "taskflow",
                clientLabel = "TaskFlow",
                storeInstanceId = "root",
                storeName = "TaskFlow",
                serializerTier = "json",
            ),
        )
        conn.accept(
            BridgeMessage.Action(
                actionId = 1,
                action = buildJsonObject { put("type", JsonPrimitive("AddCard")) },
                state = buildJsonObject {},
                diff = emptyList(),
                timestampMillis = 1L,
                isExcess = false,
            ),
        )

        flushAll(ingest, dir)

        val ref = resolveStore(dir, null)
        val actions = readCapture(ref.file).second
        assertEquals(listOf("AddCard"), actions.map { actionType(it.action) })
        assertTrue(formatRecord(actions.first(), Format.ACTIONS, ref.key).contains("\"type\":\"AddCard\""))
    }
}
