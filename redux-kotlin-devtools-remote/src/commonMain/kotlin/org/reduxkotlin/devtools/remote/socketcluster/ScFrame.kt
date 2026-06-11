package org.reduxkotlin.devtools.remote.socketcluster

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** A decoded inbound SocketCluster frame. */
internal sealed interface ScInbound {
    /**
     * A server heartbeat; the client must answer with [reply] — the empty string for the protocol-v2
     * empty-string ping, `"#2"` for the legacy `"#1"` ping.
     */
    data class Ping(val reply: String) : ScInbound

    /** A response to a prior `invoke`/`#handshake`/`#subscribe`, correlated by [rid]. */
    data class RpcResponse(val rid: Int, val data: JsonElement?, val error: JsonElement?) : ScInbound

    /** A message published on a subscribed [channel]. */
    data class ChannelMessage(val channel: String, val data: JsonElement) : ScInbound

    /** Any other frame we don't act on (e.g. `#removeAuthToken`, server pings we ignore). */
    data class Other(val raw: String) : ScInbound
}

/** Encodes/decodes the minimal SocketCluster v2 wire protocol used by Redux DevTools Remote. */
internal object ScFrame {
    /** The protocol-v2 heartbeat reply: an empty text frame. */
    const val PONG: String = ""

    /** The legacy (SC v1-style) heartbeat reply to a `"#1"` ping. */
    const val LEGACY_PONG: String = "#2"

    private val json = Json { encodeDefaults = true }

    fun encodeHandshake(cid: Int): String = """{"event":"#handshake","data":{},"cid":$cid}"""

    fun encodeInvoke(event: String, data: JsonElement, cid: Int): String {
        val obj = JsonObject(
            mapOf(
                "event" to JsonPrimitive(event),
                "data" to data,
                "cid" to JsonPrimitive(cid),
            ),
        )
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    fun encodeSubscribe(channel: String, cid: Int): String {
        val obj = JsonObject(
            mapOf(
                "event" to JsonPrimitive("#subscribe"),
                "data" to JsonObject(mapOf("channel" to JsonPrimitive(channel))),
                "cid" to JsonPrimitive(cid),
            ),
        )
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    fun encodeTransmit(event: String, data: JsonElement): String {
        val obj = JsonObject(
            mapOf(
                "event" to JsonPrimitive(event),
                "data" to data,
            ),
        )
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    fun decode(raw: String): ScInbound = when {
        // SC v2 empty-string heartbeat: answered with an empty frame.
        raw.isEmpty() -> ScInbound.Ping(PONG)

        // Legacy server ping token: the legacy protocol expects "#2" back, not "".
        raw == "#1" -> ScInbound.Ping(LEGACY_PONG)

        else -> decodeJson(raw)
    }

    private fun decodeJson(raw: String): ScInbound {
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return ScInbound.Other(raw)
        return decodeObject(obj, raw)
    }

    private fun decodeObject(obj: JsonObject, raw: String): ScInbound {
        val rid = (obj["rid"] as? JsonPrimitive)?.intOrNull
        return when {
            rid != null -> ScInbound.RpcResponse(rid = rid, data = obj["data"], error = obj["error"])
            (obj["event"] as? JsonPrimitive)?.content == "#publish" -> decodePublish(obj, raw)
            else -> ScInbound.Other(raw)
        }
    }

    /** Decodes a `#publish` frame: `{event:#publish, data:{channel, data}}` → [ScInbound.ChannelMessage]. */
    private fun decodePublish(obj: JsonObject, raw: String): ScInbound {
        val payload = obj["data"] as? JsonObject
        val channel = (payload?.get("channel") as? JsonPrimitive)?.content
        return if (payload != null && channel != null) {
            ScInbound.ChannelMessage(channel = channel, data = payload["data"] ?: JsonObject(emptyMap()))
        } else {
            ScInbound.Other(raw)
        }
    }
}
