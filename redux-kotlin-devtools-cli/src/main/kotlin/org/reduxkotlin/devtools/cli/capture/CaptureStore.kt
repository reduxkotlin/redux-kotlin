package org.reduxkotlin.devtools.cli.capture

import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.decodeRecording
import java.io.File

/** A store discovered in a capture directory. */
internal data class StoreRef(val key: String, val name: String, val file: File)

/** Convert a wire store key (`clientId::storeInstanceId`) into a filesystem-safe base name. */
internal fun safeKey(storeKey: String): String = storeKey.replace("::", "__").replace(Regex("[^A-Za-z0-9_.-]"), "_")

/** Capture file name for a store key. */
internal fun captureFileName(storeKey: String): String = "${safeKey(storeKey)}.jsonl"

/** List the store recordings present in [dir] by reading each file's header. */
internal fun discoverStores(dir: File): List<StoreRef> {
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }
        ?.mapNotNull { f ->
            runCatching {
                val header: RecordingHeader = decodeRecording(f.readText()).first
                StoreRef(key = "${header.clientId}::${header.storeInstanceId}", name = header.storeName, file = f)
            }.getOrNull()
        }
        ?.sortedBy { it.key }
        .orEmpty()
}
