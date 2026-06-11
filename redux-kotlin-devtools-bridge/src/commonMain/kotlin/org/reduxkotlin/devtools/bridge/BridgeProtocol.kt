package org.reduxkotlin.devtools.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.DiffEntry
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace

/** Bridge wire-protocol version; bumped on any incompatible envelope change. */
public const val PROTOCOL_VERSION: Int = 1

/** Shared Json for the bridge wire (ignores unknown keys so older/newer peers interoperate). */
public val bridgeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

/**
 * The bridge wire envelope. A versioned, `@Serializable` mirror of the events that cross the socket —
 * decoupled from the core sealed `DevToolsEvent` so the protocol can evolve independently. The first
 * frame is always a [Hello]; the monitor replies [HelloAck].
 */
@Serializable
public sealed interface BridgeMessage {

    /** Handshake: identifies the client + store and negotiates the protocol. Sent first. */
    @Serializable
    @SerialName("hello")
    public data class Hello(
        /** Sender's protocol version. */
        public val protocolVersion: Int,
        /** Stable id of the debugged app instance. */
        public val clientId: String,
        /** Human label for the client (device/app). */
        public val clientLabel: String,
        /** The store's `instanceId ?: name`. */
        public val storeInstanceId: String,
        /** The store's display name. */
        public val storeName: String,
        /**
         * Which `ValueSerializer` tier produced the JSON. Best-effort: core does not expose a
         * session's serializer, so senders that cannot determine the tier send `"unknown"`
         * (the bridge output currently always does).
         */
        public val serializerTier: String,
        /** Shared token; required by the monitor for non-loopback connections. */
        public val token: String? = null,
    ) : BridgeMessage

    /** Monitor's handshake reply with the accepted protocol version. */
    @Serializable
    @SerialName("ack")
    public data class HelloAck(
        /** Protocol version the monitor will speak. */
        public val protocolVersion: Int,
        /** `false` + [reason] when the monitor refuses. */
        public val accepted: Boolean = true,
        /** Refusal reason, when not accepted. */
        public val reason: String? = null,
    ) : BridgeMessage

    /** The store's initial serialized state. */
    @Serializable
    @SerialName("init")
    public data class Init(
        /** Serialized preloaded state. */
        public val state: JsonElement,
    ) : BridgeMessage

    /** A recorded action + resulting state + diff. */
    @Serializable
    @SerialName("action")
    public data class Action(
        /** Recorder id. */
        public val actionId: Int,
        /** Serialized action (carries its `type`). */
        public val action: JsonElement,
        /** Serialized resulting state. */
        public val state: JsonElement,
        /** Leaf diff vs the previous state. */
        public val diff: List<DiffEntry>,
        /** Dispatch-time capture, epoch millis. */
        public val timestampMillis: Long,
        /** Ring-buffer eviction flag. */
        public val isExcess: Boolean,
    ) : BridgeMessage

    /** The static pipeline structure (sent once when registered). */
    @Serializable
    @SerialName("pipeline")
    public data class PipelineRegistered(
        /** The node map. */
        public val structure: PipelineStructure,
    ) : BridgeMessage

    /** A per-action pipeline trace. */
    @Serializable
    @SerialName("trace")
    public data class PipelineTraced(
        /** Per-action node trace. */
        public val trace: PipelineTrace,
    ) : BridgeMessage
}

/** Maps a core [DevToolsEvent] to its wire envelope. */
public fun toWire(event: DevToolsEvent): BridgeMessage = when (event) {
    is DevToolsEvent.Initialized -> BridgeMessage.Init(event.state)

    is DevToolsEvent.ActionRecorded -> BridgeMessage.Action(
        event.actionId,
        event.action,
        event.state,
        event.diff,
        event.timestampMillis,
        event.isExcess,
    )

    is DevToolsEvent.PipelineRegistered -> BridgeMessage.PipelineRegistered(event.structure)

    is DevToolsEvent.PipelineTraced -> BridgeMessage.PipelineTraced(event.trace)
}
