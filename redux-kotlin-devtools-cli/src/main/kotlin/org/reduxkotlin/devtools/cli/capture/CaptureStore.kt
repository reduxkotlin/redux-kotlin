package org.reduxkotlin.devtools.cli.capture

import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.bridgeJson
import java.io.File

/** A store discovered in a capture directory. */
internal data class StoreRef(val key: String, val name: String, val file: File)

/** Convert a wire store key (`clientId::storeInstanceId`) into a filesystem-safe base name. */
internal fun safeKey(storeKey: String): String = storeKey.replace("::", "__").replace(Regex("[^A-Za-z0-9_.-]"), "_")

/** Capture file name for a store key. */
internal fun captureFileName(storeKey: String): String = "${safeKey(storeKey)}.jsonl"

/**
 * List the store recordings present in [dir] by decoding only each file's first line as a
 * [RecordingHeader]. Files whose first line doesn't parse are skipped — a corrupt or in-progress
 * message line later in a file never hides the store.
 */
internal fun discoverStores(dir: File): List<StoreRef> {
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }
        ?.mapNotNull { f ->
            runCatching {
                val firstLine = f.useLines { lines -> lines.firstOrNull { it.isNotBlank() } } ?: return@mapNotNull null
                val header = bridgeJson.decodeFromString(RecordingHeader.serializer(), firstLine)
                StoreRef(key = "${header.clientId}::${header.storeInstanceId}", name = header.storeName, file = f)
            }.getOrNull()
        }
        ?.sortedBy { it.key }
        .orEmpty()
}
