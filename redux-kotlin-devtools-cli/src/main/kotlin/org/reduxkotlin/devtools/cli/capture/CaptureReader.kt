package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.bridge.bridgeJson
import java.io.File

/** Parse capture [text]: first line is the header, subsequent lines are messages; a trailing partial line is skipped. */
internal fun parseCapture(text: String): Pair<RecordingHeader, List<BridgeMessage.Action>> {
    val lines = text.split("\n").filter { it.isNotBlank() }
    require(lines.isNotEmpty()) { "empty capture" }
    val header = bridgeJson.decodeFromString(RecordingHeader.serializer(), lines.first())
    val actions = lines.drop(1).mapNotNull { line ->
        runCatching { bridgeJson.decodeFromString(BridgeMessage.serializer(), line) }
            .getOrNull() as? BridgeMessage.Action
    }
    return header to actions
}

/** Read a capture [file] from disk. */
internal fun readCapture(file: File): Pair<RecordingHeader, List<BridgeMessage.Action>> = parseCapture(file.readText())

/** Render the `type` discriminant of a serialized action for display/filtering. */
internal fun actionType(action: JsonElement): String =
    ((action as? JsonObject)?.get("type") as? JsonPrimitive)?.content ?: "?"
