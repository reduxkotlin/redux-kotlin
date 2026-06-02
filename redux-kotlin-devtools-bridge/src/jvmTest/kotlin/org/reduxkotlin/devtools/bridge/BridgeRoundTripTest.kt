package org.reduxkotlin.devtools.bridge

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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

class BridgeRoundTripTest {
    private val port = ephemeralPort()
    private val received = CopyOnWriteArrayList<BridgeMessage>()

    private val server = embeddedServer(ServerCIO, port = port) {
        install(ServerWebSockets)
        routing {
            webSocket("/bridge") {
                for (frame in incoming) {
                    val msg = (frame as? Frame.Text)?.let { f ->
                        runCatching {
                            bridgeJson.decodeFromString(BridgeMessage.serializer(), f.readText())
                        }.getOrNull()
                    } ?: continue
                    received.add(msg)
                    if (msg is BridgeMessage.Hello) {
                        send(
                            bridgeJson.encodeToString(
                                BridgeMessage.serializer(),
                                BridgeMessage.HelloAck(PROTOCOL_VERSION, accepted = true),
                            ),
                        )
                    }
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
    fun bridgeOutput_sends_hello_then_init_after_session_init() = runBlocking {
        val session = DevToolsSession.create(DevToolsConfig(name = "roundtrip-test"))
        val out = BridgeOutput(BridgeConfig(port = port))
        out.start(session)
        session.init(mapOf("count" to 0))

        withTimeout(10_000L) {
            while (true) {
                val hasHello = received.any { it is BridgeMessage.Hello }
                val hasInit = received.any { it is BridgeMessage.Init }
                if (hasHello && hasInit) break
                kotlinx.coroutines.delay(50)
            }
        }

        out.stop()
        session.close()

        assertTrue(received.any { it is BridgeMessage.Hello }, "expected a Hello frame")
        assertTrue(received.any { it is BridgeMessage.Init }, "expected an Init frame")
    }

    @Test
    fun bridgeOutput_sends_init_or_action_after_record() = runBlocking {
        val session = DevToolsSession.create(DevToolsConfig(name = "roundtrip-action"))
        val out = BridgeOutput(BridgeConfig(port = port))
        out.start(session)
        session.init(mapOf("count" to 0))
        session.record(mapOf("type" to "INCREMENT"), mapOf("count" to 1))

        withTimeout(10_000L) {
            while (true) {
                val hasHello = received.any { it is BridgeMessage.Hello }
                val hasInitOrAction = received.any { it is BridgeMessage.Init || it is BridgeMessage.Action }
                if (hasHello && hasInitOrAction) break
                kotlinx.coroutines.delay(50)
            }
        }

        out.stop()
        session.close()

        assertTrue(received.any { it is BridgeMessage.Hello }, "expected a Hello frame")
        assertTrue(
            received.any { it is BridgeMessage.Init || it is BridgeMessage.Action },
            "expected an Init or Action frame",
        )
    }
}
