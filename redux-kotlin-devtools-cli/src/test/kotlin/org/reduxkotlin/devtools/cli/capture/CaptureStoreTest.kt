package org.reduxkotlin.devtools.cli.capture

import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.bridgeJson
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CaptureStoreTest {
    @Test
    fun safeKey_sanitizes_separator_and_unsafe_chars() {
        assertEquals("taskflow__TaskFlow-root", safeKey("taskflow::TaskFlow-root"))
        assertEquals("a_b__c_d", safeKey("a/b::c d"))
    }

    private fun headerLine(client: String, instance: String): String = bridgeJson.encodeToString(
        RecordingHeader.serializer(),
        RecordingHeader(
            protocolVersion = PROTOCOL_VERSION,
            serializerTier = "json",
            clientId = client,
            clientLabel = client,
            storeName = client,
            storeInstanceId = instance,
        ),
    )

    @Test
    fun discoverStores_only_needs_a_parsable_first_line() {
        val dir = Files.createTempDirectory("rkdisc").toFile()
        // Valid header followed by a corrupt message line must NOT hide the store.
        dir.resolve("good.jsonl").writeText(headerLine("app", "root") + "\n{\"t\":\"acti")
        // A file whose first line doesn't parse is skipped without failing the listing.
        dir.resolve("bad.jsonl").writeText("not json at all\n" + headerLine("app", "other"))

        assertEquals(listOf("app::root"), discoverStores(dir).map { it.key })
    }
}
