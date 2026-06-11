package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.UsageError
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
    fun no_captures_throws_a_usage_error() {
        val dir = java.nio.file.Files.createTempDirectory("rkq-empty").toFile()
        assertFailsWith<UsageError> { resolveStore(dir, null) }
    }

    @Test
    fun multiple_stores_require_the_flag() {
        val dir = Files.createTempDirectory("rkq").toFile()
        seed(dir, "app::root")
        seed(dir, "app::acct")
        assertFailsWith<UsageError> { resolveStore(dir, null) }
        assertEquals("app::acct", resolveStore(dir, "app::acct").key)
    }

    @Test
    fun time_flags_accept_epoch_millis_and_iso_instants() {
        assertEquals(1718000000000L, parseTimeMillis("1718000000000"))
        assertEquals(0L, parseTimeMillis("1970-01-01T00:00:00Z"))
        assertFailsWith<Exception> { parseTimeMillis("yesterday") }
    }
}
