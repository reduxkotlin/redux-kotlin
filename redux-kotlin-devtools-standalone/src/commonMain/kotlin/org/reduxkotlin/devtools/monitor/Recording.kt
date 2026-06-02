package org.reduxkotlin.devtools.monitor

import kotlinx.serialization.Serializable
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.bridgeJson

/** First line of a `.jsonl` recording — pins the format + provenance for forward compatibility. */
@Serializable
public data class RecordingHeader(
    /** `"rk-devtools-recording"` discriminator. */
    public val kind: String = "rk-devtools-recording",
    /** Bridge protocol version the events use. */
    public val protocolVersion: Int,
    /** Serializer tier that produced the JSON. */
    public val serializerTier: String,
    /** Source client id. */
    public val clientId: String,
    /** Source client label. */
    public val clientLabel: String,
    /** Source store name. */
    public val storeName: String,
    /** Source store instance id. */
    public val storeInstanceId: String,
)

/** Encodes a header + messages to newline-delimited JSON (`.jsonl`). */
public fun encodeRecording(header: RecordingHeader, messages: List<BridgeMessage>): String = buildString {
    appendLine(bridgeJson.encodeToString(RecordingHeader.serializer(), header))
    messages.forEach { appendLine(bridgeJson.encodeToString(BridgeMessage.serializer(), it)) }
}

/** Decodes a `.jsonl` recording into its header + messages (ignores blank lines). */
public fun decodeRecording(text: String): Pair<RecordingHeader, List<BridgeMessage>> {
    val lines = text.split("\n").filter { it.isNotBlank() }
    require(lines.isNotEmpty()) { "empty recording" }
    val header = bridgeJson.decodeFromString(RecordingHeader.serializer(), lines.first())
    val messages = lines.drop(1).map { bridgeJson.decodeFromString(BridgeMessage.serializer(), it) }
    return header to messages
}

/** Writes [text] as a recording file/blob named [suggestedName]; platform-specific. */
public expect fun saveRecording(suggestedName: String, text: String)

/** Prompts for + reads a recording file/blob; calls [onLoaded] with its contents (async on web). */
public expect fun loadRecording(onLoaded: (String) -> Unit)
