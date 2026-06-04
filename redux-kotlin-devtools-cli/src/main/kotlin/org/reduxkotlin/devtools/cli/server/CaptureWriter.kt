package org.reduxkotlin.devtools.cli.server

import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.encodeRecording
import org.reduxkotlin.devtools.cli.capture.captureFileName
import java.io.File

/** Atomically write a store's recording into [dir] as `<safeKey>.jsonl`; returns the file. */
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
    tmp.copyTo(target, overwrite = true)
    tmp.delete()
    return target
}
