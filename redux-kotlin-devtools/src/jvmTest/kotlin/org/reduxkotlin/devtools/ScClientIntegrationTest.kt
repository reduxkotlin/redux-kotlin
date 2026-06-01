package org.reduxkotlin.devtools

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.devtools.socketcluster.ScClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixed port unlikely to collide in CI. */
private const val TEST_PORT = 18769

class ScClientIntegrationTest {
    private val logReceived = CompletableDeferred<String>()
    private val server = embeddedServer(ServerCIO, port = TEST_PORT) {
        install(ServerWebSockets)
        routing {
            webSocket("/socketcluster/") {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    when {
                        text.contains("#handshake") ->
                            send("""{"rid":1,"data":{"id":"sock-test","isAuthenticated":false}}""")
                        text.contains("\"login\"") -> {
                            val cid = Regex("\"cid\":(\\d+)").find(text)!!.groupValues[1]
                            send("""{"rid":$cid,"data":"chan-test"}""")
                        }
                        text.contains("#subscribe") -> {
                            val cid = Regex("\"cid\":(\\d+)").find(text)!!.groupValues[1]
                            send("""{"rid":$cid}""")
                        }
                        text.contains("\"event\":\"log\"") -> logReceived.complete(text)
                    }
                }
            }
        }
    }

    @BeforeTest
    fun startServer() {
        server.start(wait = false)
    }

    @AfterTest
    fun stopServer() {
        server.stop(100, 100)
    }

    @Test
    fun completesHandshakeLoginSubscribeAndTransmitsLog() = runBlocking {
        val client = createDevToolsHttpClient()
        client.webSocket(host = "127.0.0.1", port = TEST_PORT, path = "/socketcluster/") {
            val sc = ScClient(this, onChannelMessage = { _, _ -> }, log = {})
            launch { sc.readLoop() }
            sc.handshake()
            assertEquals("sock-test", sc.id)
            val channel = sc.login("master")
            assertEquals("chan-test", channel)
            sc.subscribe(channel)
            sc.transmitLog(JsonObject(mapOf("type" to JsonPrimitive("START"))))
            val received = withTimeout(5_000) { logReceived.await() }
            assertTrue(received.contains("\"event\":\"log\""))
        }
        client.close()
    }
}
