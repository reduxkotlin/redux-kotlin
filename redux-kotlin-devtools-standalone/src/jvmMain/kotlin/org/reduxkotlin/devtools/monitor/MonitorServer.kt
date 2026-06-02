package org.reduxkotlin.devtools.monitor

import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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

/**
 * An embedded Ktor WebSocket server that accepts bridge clients, performs the [BridgeMessage.Hello]
 * handshake (token-gated for non-loopback peers), and decodes incoming frames into [MonitorIngest].
 *
 * @param ingest the ingest that receives decoded [BridgeMessage]s.
 * @param port listen port; `0` binds an ephemeral port (resolved by [start]).
 * @param host bind address (default loopback).
 * @param token shared secret; required by non-loopback clients when set.
 */
public class MonitorServer(
    private val ingest: MonitorIngest,
    private val port: Int = 9090,
    private val host: String = "127.0.0.1",
    private val token: String? = null,
) {
    private val loopbackHosts = setOf("127.0.0.1", "::1", "localhost")

    private val server = embeddedServer(CIO, port = port, host = host) {
        install(ServerWebSockets)
        routing {
            // Minimal web host: serves an index that boots the compiled wasmJs bundle. The bundle
            // itself (`redux-kotlin-devtools-standalone.js` + `.wasm`) must be copied onto the
            // classpath/working dir for the browser to fetch it.
            // TODO(web-bundle): wire the wasmJsBrowserDistribution output into Ktor static-serving
            //  (e.g. `staticFiles("/", File(distDir))`) via a Gradle task that copies
            //  `build/dist/wasmJs/productionExecutable` next to the server. Until then this index
            //  expects those assets to be reachable same-origin; the `/bridge` WS already works.
            get("/") {
                call.respondText(WEB_INDEX_HTML, ContentType.Text.Html)
            }
            webSocket("/bridge") {
                println("[rk-monitor] /bridge connection opened")
                val conn = ingest.openConnection()
                try {
                    for (frame in incoming) {
                        val msg = (frame as? Frame.Text)
                            ?.readText()
                            ?.let {
                                runCatching {
                                    bridgeJson.decodeFromString(
                                        BridgeMessage.serializer(),
                                        it,
                                    )
                                }.getOrNull()
                            }
                            ?: continue
                        if (msg is BridgeMessage.Hello) {
                            val loopback = host in loopbackHosts
                            val ok = (loopback || (token != null && msg.token == token)) &&
                                msg.protocolVersion == PROTOCOL_VERSION
                            println("[rk-monitor] Hello client=${msg.clientId} store=${msg.storeName} v=${msg.protocolVersion} accepted=$ok")
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
     */
    public fun start(): Int {
        server.start(wait = false)
        val bound = runBlocking { server.engine.resolvedConnectors().first().port }
        println("[rk-monitor] WS server listening on $host:$bound/bridge")
        return bound
    }

    /** Stops the server. */
    public fun stop() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 100)
    }

    private companion object {
        /** Boots the compiled wasmJs bundle; assets must be served same-origin (see TODO above). */
        private val WEB_INDEX_HTML = """
            <!doctype html>
            <html lang="en">
            <head><meta charset="utf-8"><title>Redux DevTools Monitor</title></head>
            <body>
              <script type="application/javascript" src="redux-kotlin-devtools-standalone.js"></script>
            </body>
            </html>
        """.trimIndent()
    }
}
