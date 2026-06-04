package org.reduxkotlin.devtools.cli.server

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.cli.capture.discoverStores
import org.reduxkotlin.devtools.cli.capture.readCapture
import org.reduxkotlin.devtools.monitor.MonitorIngest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CaptureFlusherTest {
    @Test
    fun flushes_each_store_to_a_capture_file() {
        val dir = Files.createTempDirectory("rkflush").toFile()
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
                action = buildJsonObject { put("type", JsonPrimitive("A")) },
                state = buildJsonObject {},
                diff = emptyList(),
                timestampMillis = 1L,
                isExcess = false,
            ),
        )

        flushAll(ingest, dir)

        val stores = discoverStores(dir)
        assertEquals(listOf("taskflow::root"), stores.map { it.key })
        assertEquals(listOf(1), readCapture(stores.first().file).second.map { it.actionId })
    }
}
