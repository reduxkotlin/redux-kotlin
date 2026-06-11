package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.decodeRecordingLenient
import java.io.File

/**
 * Read a capture [file] from disk, keeping only the action records. Decoding is lenient
 * (`decodeRecordingLenient`): undecodable lines — e.g. a trailing partial write — are skipped.
 */
internal fun readCapture(file: File): Pair<RecordingHeader, List<BridgeMessage.Action>> {
    val (header, messages) = decodeRecordingLenient(file.readText())
    return header to messages.filterIsInstance<BridgeMessage.Action>()
}

/** Render the `type` discriminant of a serialized action for display/filtering. */
internal fun actionType(action: JsonElement): String =
    ((action as? JsonObject)?.get("type") as? JsonPrimitive)?.content ?: "?"
