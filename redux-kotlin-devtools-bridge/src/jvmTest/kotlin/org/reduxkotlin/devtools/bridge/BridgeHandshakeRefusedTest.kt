package org.reduxkotlin.devtools.bridge

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsSession
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/** Allocate an ephemeral loopback port. */
private fun ephemeralPort(): Int = ServerSocket(0).use { it.localPort }

class BridgeHandshakeRefusedTest {
    private val port = ephemeralPort()
    private val logged = CopyOnWriteArrayList<String>()

    private val server = embeddedServer(ServerCIO, port = port) {
        install(ServerWebSockets)
        routing {
            webSocket("/bridge") {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    send(
                        bridgeJson.encodeToString(
                            BridgeMessage.serializer(),
                            BridgeMessage.HelloAck(PROTOCOL_VERSION, accepted = false, reason = "bad token"),
                        ),
                    )
                }
            }
        }
    }

    @BeforeTest
    fun startServer() {
        server.start(wait = false)
        // Give CIO time to bind the port before the client tries to connect.
        Thread.sleep(200)
    }

    @AfterTest
    fun stopServer() {
        server.stop(100, 100)
    }

    @Test
    fun refused_handshake_is_logged_and_never_reaches_the_host() = runBlocking {
        val session = DevToolsSession.create(DevToolsConfig(name = "refused-test"))
        val out = BridgeOutput(BridgeConfig(port = port), logger = { logged.add(it) })
        out.start(session)

        withTimeout(10_000L) {
            while (logged.none { it.contains("handshake refused") }) {
                kotlinx.coroutines.delay(50)
            }
        }

        out.stop()
        session.close()

        assertTrue(
            logged.any { it.contains("handshake refused") && it.contains("bad token") },
            "expected a refused-handshake log with the reason, got: $logged",
        )
    }
}
