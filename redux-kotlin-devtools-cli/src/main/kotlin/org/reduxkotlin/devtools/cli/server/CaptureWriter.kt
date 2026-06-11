package org.reduxkotlin.devtools.cli.server

import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.encodeRecording
import org.reduxkotlin.devtools.cli.capture.captureFileName
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Atomically write a store's recording into [dir] as `<safeKey>.jsonl`; returns the file.
 * The recording is staged to a `.tmp` sibling and renamed into place with `ATOMIC_MOVE`, so
 * concurrent readers (`tail --follow`, `stores`) never observe a half-written file. Filesystems
 * that refuse atomic moves fall back to a plain replace.
 */
internal fun writeStoreCapture(
    dir: File,
    storeKey: String,
    header: RecordingHeader,
    messages: List<BridgeMessage>,
): File {
    dir.mkdirs()
    val target = File(dir, captureFileName(storeKey))
    val tmp = File(dir, target.name + ".tmp")
    tmp.writeText(encodeRecording(header, messages))
    try {
        Files.move(
            tmp.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    return target
}
