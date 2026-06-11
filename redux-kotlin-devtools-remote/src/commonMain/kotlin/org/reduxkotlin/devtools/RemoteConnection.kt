package org.reduxkotlin.devtools.remote

import io.ktor.client.plugins.websocket.webSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.remote.socketcluster.ScClient
import org.reduxkotlin.devtools.remote.wire.MessageContext
import org.reduxkotlin.devtools.remote.wire.startMessage
import org.reduxkotlin.devtools.remote.wire.stateMessage

/**
 * Owns the WebSocket connection lifecycle and a buffered outbound queue for the [RemoteOutput].
 * Callers only enqueue pre-built JSON messages; all IO happens on this connection's own coroutine,
 * and failures never propagate out. Recording (serialization/diffing) lives in core's
 * [DevToolsSession]; this type just relays already-serialized wire messages over SocketCluster.
 *
 * The connect loop reconnects with exponential backoff. On each (re)connect it sends a `START`
 * message and, when the monitor requests state (inbound `START`/`UPDATE`), replies with the
 * session's current lifted state — so a late-joining monitor always gets a full snapshot.
 *
 * @param remoteConfig connection settings (host/port/secure).
 * @param session the recording session; supplies identity ([DevToolsSession.id]) and the
 *   lifted-state snapshot replayed on (re)connect.
 * @param logger sink for connection diagnostics; defaults to a no-op.
 */
internal class RemoteConnection(
    private val remoteConfig: RemoteConfig,
    private val session: DevToolsSession,
    private val logger: (String) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outbound = Channel<JsonObject>(capacity = 256)

    // Core's DevToolsSession does not expose its DevToolsConfig, so identity is derived from the
    // session's stable id (which equals the recording config's instanceId ?: name).
    private val name = session.id
    private val instanceId = session.id
    private val client = createDevToolsHttpClient()

    fun start() {
        scope.launch { connectLoop() }
    }

    fun enqueue(message: JsonObject) {
        val result = outbound.trySend(message)
        if (result.isFailure) logger("devtools: outbound buffer full, dropping message")
    }

    /** Convenience wrapper that builds and enqueues a `STATE` message for [lifted]. */
    fun enqueueState(lifted: JsonObject) {
        val ctx = MessageContext(socketId = null, name = name, instanceId = instanceId)
        enqueue(stateMessage(ctx, lifted))
    }

    /**
     * Stops the connection. Normally unnecessary — a debug session lives for the app's lifetime —
     * but provided for tests and explicit teardown.
     */
    fun stop() {
        scope.cancel()
        outbound.close()
        client.close()
    }

    @Suppress("TooGenericExceptionCaught") // broad swallow is intentional: IO failures must never reach the store
    private suspend fun connectLoop() {
        var backoffMs = 500L
        while (scope.isActive) {
            try {
                logger("devtools: connecting to ${scheme()}://${remoteConfig.host}:${remoteConfig.port}")
                client.webSocket(
                    host = remoteConfig.host,
                    port = remoteConfig.port,
                    path = "/socketcluster/",
                ) {
                    var ctx = MessageContext(socketId = null, name = name, instanceId = instanceId)
                    var scRef: ScClient? = null
                    val sc = ScClient(
                        session = this,
                        onChannelMessage = { _, data -> handleInbound(data, scRef?.id) { enqueue(it) } },
                        log = logger,
                    )
                    scRef = sc
                    launch { sc.readLoop() }
                    sc.handshake()
                    ctx = ctx.copy(socketId = sc.id)
                    val channel = sc.login("master")
                    sc.subscribe(channel)
                    sc.transmitLog(startMessage(ctx))
                    logger("devtools: connected")
                    backoffMs = 500L
                    for (message in outbound) {
                        sc.transmitLog(stampId(message, sc.id))
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                logger("devtools: connection failed (${e.message}); retrying in ${backoffMs}ms")
            }
            if (!scope.isActive) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private inline fun handleInbound(data: JsonElement, socketId: String?, send: (JsonObject) -> Unit) {
        val type = (data as? JsonObject)?.get("type")?.let { (it as? JsonPrimitive)?.content } ?: return
        when (type) {
            "START", "UPDATE" -> {
                val ctx = MessageContext(socketId = socketId, name = name, instanceId = instanceId)
                send(stateMessage(ctx, session.liftedState()))
            }

            else -> logger("devtools: ignoring inbound '$type'")
        }
    }

    private fun stampId(message: JsonObject, id: String?): JsonObject {
        if (id == null) return message
        val map = message.toMutableMap()
        map["id"] = JsonPrimitive(id)
        return JsonObject(map)
    }

    private fun scheme() = if (remoteConfig.secure) "wss" else "ws"

    private companion object {
        // Kept in sync with the bridge module's BridgeConnection.MAX_BACKOFF_MS.
        const val MAX_BACKOFF_MS = 8_000L
    }
}
