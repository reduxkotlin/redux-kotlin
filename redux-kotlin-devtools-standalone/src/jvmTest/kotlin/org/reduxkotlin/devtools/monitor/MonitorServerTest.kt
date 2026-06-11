package org.reduxkotlin.devtools.monitor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.bridge.BridgeConfig
import org.reduxkotlin.devtools.bridge.BridgeOutput
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonitorServerTest {

    @Test
    fun loopback_peers_are_recognized() {
        assertTrue(isLoopbackHost("127.0.0.1"))
        assertTrue(isLoopbackHost("127.0.0.53"))
        assertTrue(isLoopbackHost("::1"))
        assertTrue(isLoopbackHost("0:0:0:0:0:0:0:1"))
        assertTrue(isLoopbackHost("localhost"))
        assertFalse(isLoopbackHost("192.168.1.20"))
        assertFalse(isLoopbackHost("0.0.0.0"))
        assertFalse(isLoopbackHost("example.com"))
    }

    @Test
    fun start_refuses_a_non_loopback_bind_without_a_token() {
        val server = MonitorServer(MonitorIngest(), port = 0, host = "0.0.0.0", token = null)
        val e = assertFailsWith<IllegalStateException> { server.start() }
        assertTrue(e.message!!.contains("token"))
    }

    // A loopback peer connecting WITHOUT a token must be accepted: the handshake below carries no
    // token, and the store only appears in the registry after an accepted HelloAck.
    @Test
    fun a_bridge_client_connection_populates_the_registry() = runBlocking {
        val ingest = MonitorIngest()
        val server = MonitorServer(ingest, port = 0) // 0 = ephemeral
        val port = server.start()
        try {
            val session = DevToolsSession.create(DevToolsConfig(name = "TaskFlow-root"))
            val out = BridgeOutput(
                BridgeConfig(host = "127.0.0.1", port = port, clientId = "tf", clientLabel = "TaskFlow"),
            )
            // Connect bridge first; wait for the store to appear (Hello handshake complete).
            out.start(session)
            withTimeout(8_000) {
                ingest.registry.state.filter { it.stores.isNotEmpty() }.first()
            }
            // Now dispatch — events stream live into the connected bridge.
            session.init(mapOf("n" to 0))
            session.record(mapOf("type" to "INCREMENT"), mapOf("n" to 1))
            withTimeout(8_000) {
                while (ingest.registry.state.value.stores.firstOrNull()?.state?.actions?.isEmpty() != false) delay(50)
            }
            out.stop()
            session.close()
            val store = ingest.registry.state.value.stores.single()
            assertTrue(store.ref.id.startsWith("tf::"))
            assertTrue(store.state.actions.isNotEmpty())
        } finally {
            server.stop()
        }
    }
}
