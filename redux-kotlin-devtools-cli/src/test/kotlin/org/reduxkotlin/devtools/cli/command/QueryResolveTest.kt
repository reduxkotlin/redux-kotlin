package org.reduxkotlin.devtools.cli.command

import kotlinx.serialization.json.buildJsonObject
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.cli.server.writeStoreCapture
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class QueryResolveTest {
    private fun seed(dir: java.io.File, key: String) {
        val (c, s) = key.split("::")
        writeStoreCapture(
            dir,
            key,
            RecordingHeader(
                protocolVersion = PROTOCOL_VERSION,
                serializerTier = "json",
                clientId = c,
                clientLabel = c,
                storeName = c,
                storeInstanceId = s,
            ),
            listOf(BridgeMessage.Init(buildJsonObject {})),
        )
    }

    @Test
    fun single_store_resolves_without_flag() {
        val dir = Files.createTempDirectory("rkq").toFile()
        seed(dir, "app::root")
        assertEquals("app::root", resolveStore(dir, null).key)
    }

    @Test
    fun no_captures_throws() {
        val dir = java.nio.file.Files.createTempDirectory("rkq-empty").toFile()
        kotlin.test.assertFailsWith<IllegalStateException> { resolveStore(dir, null) }
    }

    @Test
    fun multiple_stores_require_the_flag() {
        val dir = Files.createTempDirectory("rkq").toFile()
        seed(dir, "app::root")
        seed(dir, "app::acct")
        assertFailsWith<IllegalStateException> { resolveStore(dir, null) }
        assertEquals("app::acct", resolveStore(dir, "app::acct").key)
    }
}
