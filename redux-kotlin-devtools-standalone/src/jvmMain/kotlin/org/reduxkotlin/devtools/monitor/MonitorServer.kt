package org.reduxkotlin.devtools.monitor

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import org.reduxkotlin.devtools.bridge.bridgeJson
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/** `true` when [hostOrIp] (a peer address or a bind host) refers to the loopback interface. */
internal fun isLoopbackHost(hostOrIp: String): Boolean =
    hostOrIp == "localhost" || hostOrIp == "::1" || hostOrIp == "0:0:0:0:0:0:0:1" || hostOrIp.startsWith("127.")

/**
 * An embedded Ktor WebSocket server that accepts bridge clients, performs the [BridgeMessage.Hello]
 * handshake, and decodes incoming frames into [MonitorIngest]. The handshake is token-gated by the
 * connecting PEER's address: loopback peers connect freely; any other peer must present [token].
 * [start] refuses to bind a non-loopback [host] without a token.
 *
 * @param ingest the ingest that receives decoded [BridgeMessage]s.
 * @param port listen port; `0` binds an ephemeral port (resolved by [start]).
 * @param host bind address (default loopback).
 * @param token shared secret; required of non-loopback peers, and mandatory when [host] is
 *   non-loopback.
 */
public class MonitorServer(
    private val ingest: MonitorIngest,
    private val port: Int = 9090,
    private val host: String = "127.0.0.1",
    private val token: String? = null,
) {
    private val server = embeddedServer(CIO, port = port, host = host) {
        install(ServerWebSockets)
        routing {
            webSocket("/bridge") {
                val peerLoopback = isLoopbackHost(call.request.origin.remoteHost)
                val conn = ingest.openConnection()
                try {
                    for (frame in incoming) {
                        val msg = (frame as? Frame.Text)
                            ?.readText()
                            ?.let { text ->
                                runCatching {
                                    bridgeJson.decodeFromString(
                                        BridgeMessage.serializer(),
                                        text,
                                    )
                                }.getOrElse { e ->
                                    println(
                                        "MonitorServer: dropped undecodable frame " +
                                            "(${e::class.simpleName}): ${text.take(200)}",
                                    )
                                    null
                                }
                            }
                            ?: continue
                        if (msg is BridgeMessage.Hello) {
                            val ok = (peerLoopback || (token != null && msg.token == token)) &&
                                msg.protocolVersion == PROTOCOL_VERSION
                            send(
                                Frame.Text(
                                    bridgeJson.encodeToString(
                                        BridgeMessage.serializer(),
                                        BridgeMessage.HelloAck(
                                            protocolVersion = PROTOCOL_VERSION,
                                            accepted = ok,
                                            reason = if (ok) null else "refused",
                                        ),
                                    ),
                                ),
                            )
                            if (ok) conn.accept(msg) else return@webSocket
                        } else {
                            conn.accept(msg)
                        }
                    }
                } finally {
                    conn.close()
                }
            }
        }
    }

    /**
     * Starts the server (non-blocking) and returns the actually-bound port.
     * For ephemeral ports (`port = 0`) the resolved port is returned; otherwise [port] is returned.
     *
     * @throws IllegalStateException when [host] is non-loopback and no [token] was configured —
     *   exposing the bridge beyond the local machine requires a shared secret.
     */
    public fun start(): Int {
        check(isLoopbackHost(host) || token != null) {
            "refusing to bind non-loopback host '$host' without a token; " +
                "set a shared token to expose the bridge beyond this machine"
        }
        server.start(wait = false)
        return runBlocking { server.engine.resolvedConnectors().first().port }
    }

    /** Stops the server. */
    public fun stop() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 100)
    }
}
