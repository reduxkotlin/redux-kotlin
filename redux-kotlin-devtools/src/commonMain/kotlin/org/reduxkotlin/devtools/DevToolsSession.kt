package org.reduxkotlin.devtools

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
import org.reduxkotlin.devtools.socketcluster.ScClient
import org.reduxkotlin.devtools.wire.MessageContext
import org.reduxkotlin.devtools.wire.startMessage
import org.reduxkotlin.devtools.wire.stateMessage

/**
 * Owns the connection lifecycle and a buffered outbound queue. The dispatch path only enqueues;
 * all IO happens on this session's coroutine. Failures never propagate to the store.
 */
internal class DevToolsSession(private val config: DevToolsConfig, private val recorder: LiftedStateRecorder) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outbound = Channel<JsonObject>(capacity = 256)
    private val instanceId = config.instanceId ?: config.name
    private val client = createDevToolsHttpClient()

    fun start() {
        scope.launch { connectLoop() }
    }

    fun enqueue(message: JsonObject) {
        val result = outbound.trySend(message)
        if (result.isFailure) config.logger("devtools: outbound buffer full, dropping message")
    }

    fun stop() {
        outbound.close()
        scope.cancel()
        client.close()
    }

    @Suppress("TooGenericExceptionCaught") // broad swallow is intentional: IO failures must never reach the store
    private suspend fun connectLoop() {
        var backoffMs = 500L
        while (scope.isActive) {
            try {
                config.logger("devtools: connecting to ${scheme()}://${config.host}:${config.port}")
                client.webSocket(
                    host = config.host,
                    port = config.port,
                    path = "/socketcluster/",
                ) {
                    var ctx = MessageContext(socketId = null, name = config.name, instanceId = instanceId)
                    val sc = ScClient(
                        session = this,
                        onChannelMessage = { _, data -> handleInbound(data) { enqueue(it) } },
                        log = config.logger,
                    )
                    launch { sc.readLoop() }
                    sc.handshake()
                    ctx = ctx.copy(socketId = sc.id)
                    val channel = sc.login("master")
                    sc.subscribe(channel)
                    sc.transmitLog(startMessage(ctx))
                    config.logger("devtools: connected")
                    backoffMs = 500L
                    for (message in outbound) {
                        sc.transmitLog(stampId(message, sc.id))
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                config.logger("devtools: connection failed (${e.message}); retrying in ${backoffMs}ms")
            }
            if (!scope.isActive) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private inline fun handleInbound(data: JsonElement, send: (JsonObject) -> Unit) {
        val type = (data as? JsonObject)?.get("type")?.let { (it as? JsonPrimitive)?.content } ?: return
        when (type) {
            "START", "UPDATE" -> {
                val ctx = MessageContext(socketId = null, name = config.name, instanceId = instanceId)
                send(stateMessage(ctx, recorder.liftedState()))
            }

            else -> config.logger("devtools: ignoring inbound '$type'")
        }
    }

    private fun stampId(message: JsonObject, id: String?): JsonObject {
        if (id == null) return message
        val map = message.toMutableMap()
        map["id"] = JsonPrimitive(id)
        return JsonObject(map)
    }

    private fun scheme() = if (config.secure) "wss" else "ws"

    private companion object {
        const val MAX_BACKOFF_MS = 16_000L
    }
}
