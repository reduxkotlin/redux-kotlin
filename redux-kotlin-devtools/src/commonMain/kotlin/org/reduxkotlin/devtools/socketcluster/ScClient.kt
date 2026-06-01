package org.reduxkotlin.devtools.socketcluster

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal SocketCluster v2 client over an open [session]. Handles the handshake, the empty-string
 * heartbeat, and RPC correlation. Inbound channel messages are delivered to [onChannelMessage].
 */
internal class ScClient(
    private val session: DefaultClientWebSocketSession,
    private val onChannelMessage: suspend (channel: String, data: JsonElement) -> Unit,
    private val log: (String) -> Unit,
) {
    private var cid = 0
    private var socketId: String? = null
    private val pending = mutableMapOf<Int, CompletableDeferred<ScInbound.RpcResponse>>()
    private val pendingLock = Mutex()

    /** The socket id assigned by the server during the handshake, if any. */
    val id: String? get() = socketId

    private suspend fun nextCid(): Int = pendingLock.withLock { ++cid }

    private suspend fun invoke(encode: (Int) -> String): ScInbound.RpcResponse {
        val id = nextCid()
        val deferred = CompletableDeferred<ScInbound.RpcResponse>()
        pendingLock.withLock { pending[id] = deferred }
        session.send(encode(id))
        return deferred.await()
    }

    /** Performs the SC `#handshake` and records the assigned socket id. */
    suspend fun handshake() {
        val resp = invoke { ScFrame.encodeHandshake(it) }
        socketId = (resp.data as? JsonObject)?.get("id")?.let { (it as? JsonPrimitive)?.content }
        log("devtools: handshake ok, id=$socketId")
    }

    /** Invokes `login` with [role] and returns the channel name to subscribe to. */
    suspend fun login(role: String): String {
        val resp = invoke { ScFrame.encodeInvoke("login", JsonPrimitive(role), it) }
        return (resp.data as? JsonPrimitive)?.content ?: error("devtools: login returned no channel")
    }

    /** Subscribes to [channel]. */
    suspend fun subscribe(channel: String) {
        invoke { ScFrame.encodeSubscribe(channel, it) }
        log("devtools: subscribed to $channel")
    }

    /** Fire-and-forget publish of [data] on the `log`/`log-noid` event depending on socket id. */
    suspend fun transmitLog(data: JsonObject) {
        val event = if (socketId != null) "log" else "log-noid"
        session.send(ScFrame.encodeTransmit(event, data))
    }

    /** Reads inbound frames until the socket closes. Call once, after constructing the client. */
    suspend fun readLoop() {
        session.incoming.consumeEach { frame ->
            if (frame !is Frame.Text) return@consumeEach
            when (val inbound = ScFrame.decode(frame.readText())) {
                is ScInbound.Ping -> session.send(ScFrame.PONG)
                is ScInbound.RpcResponse -> completePending(inbound)
                is ScInbound.ChannelMessage -> onChannelMessage(inbound.channel, inbound.data)
                is ScInbound.Other -> { /* ignored */ }
            }
        }
    }

    private suspend fun completePending(resp: ScInbound.RpcResponse) {
        val deferred = pendingLock.withLock { pending.remove(resp.rid) }
        deferred?.complete(resp)
    }
}
