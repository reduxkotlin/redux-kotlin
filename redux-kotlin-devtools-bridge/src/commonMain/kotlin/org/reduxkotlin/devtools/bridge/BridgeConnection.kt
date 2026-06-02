package org.reduxkotlin.devtools.bridge

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.reduxkotlin.devtools.DevToolsSession

/**
 * The bridge's Ktor WebSocket client: handshakes, backfills the monitor with recent history via
 * [DevToolsSession.history], then drains a bounded outbound queue of wire frames. On disconnect it
 * retries with backoff and reseeds. One connection per store. Errors are swallowed/logged — never
 * propagated to the host.
 *
 * @param config connection settings.
 * @param session the store being streamed (for reseed snapshots + identity).
 * @param logger diagnostic sink.
 */
internal class BridgeConnection(
    private val config: BridgeConfig,
    private val session: DevToolsSession,
    private val logger: (String) -> Unit,
) {
    private val outbound = Channel<BridgeMessage>(capacity = 256)
    private val client = createBridgeHttpClient()
    private var scope: CoroutineScope? = null

    /** Starts the connect-loop coroutine inside [scope]. */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch { connectLoop() }
    }

    /** Enqueues a wire frame; drops and logs when the buffer is full. */
    fun enqueue(message: BridgeMessage) {
        val result = outbound.trySend(message)
        if (result.isFailure) logger("bridge: outbound buffer full, dropping message")
    }

    /** Backfills a (re)connected monitor with the recent recorded actions (no clearing Init). */
    fun reseed() {
        runCatching { session.history().forEach { enqueue(toWire(it)) } }
    }

    /** Stops the connection and closes the client. */
    fun stop() {
        outbound.close()
        runCatching { client.close() }
    }

    @Suppress("TooGenericExceptionCaught") // intentional: IO failures must never reach the store
    private suspend fun connectLoop() {
        var backoffMs = 500L
        val scheme = if (config.secure) "wss" else "ws"
        while (scope?.isActive == true) {
            try {
                logger("bridge: connecting to $scheme://${config.host}:${config.port}/bridge")
                client.webSocket(
                    host = config.host,
                    port = config.port,
                    path = "/bridge",
                ) {
                    val hello = BridgeMessage.Hello(
                        protocolVersion = PROTOCOL_VERSION,
                        clientId = config.clientId.ifBlank { session.id },
                        clientLabel = config.clientLabel,
                        storeInstanceId = session.id,
                        storeName = session.id,
                        serializerTier = "unknown",
                        token = config.token,
                    )
                    send(Frame.Text(bridgeJson.encodeToString(BridgeMessage.serializer(), hello)))
                    val ackFrame = incoming.receive() as? Frame.Text
                    val ack = ackFrame?.let {
                        bridgeJson.decodeFromString(
                            BridgeMessage.serializer(),
                            it.readText(),
                        ) as? BridgeMessage.HelloAck
                    }
                    if (ack?.accepted != true) {
                        logger("bridge: handshake refused: ${ack?.reason}")
                        return@webSocket
                    }
                    backoffMs = 500L
                    reseed()
                    for (msg in outbound) {
                        send(Frame.Text(bridgeJson.encodeToString(BridgeMessage.serializer(), msg)))
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                logger("bridge: connection failed (${e.message}); retrying in ${backoffMs}ms")
            }
            if (scope?.isActive != true) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val MAX_BACKOFF_MS = 8_000L
    }
}
