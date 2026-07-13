package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.testing.test
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies `serve`'s startup-failure paths report one-line CLI errors instead of stack traces. */
class ServeCommandTest {
    /**
     * A port already bound by another process must produce the friendly "already in use" message.
     *
     * Ktor CIO surfaces a bind failure by cancelling the engine job, so `start()` throws a
     * [kotlinx.coroutines.CancellationException] — which is an `IllegalStateException` — rather than
     * a `BindException`. The command must not mistake that for a configuration error.
     */
    @Test fun bound_port_reports_already_in_use() {
        ServerSocket().use { squatter ->
            squatter.bind(InetSocketAddress("127.0.0.1", 0))
            val port = squatter.localPort
            val result = devToolsCommand().test("serve --port $port")
            assertEquals(1, result.statusCode, "expected a failing exit code, got:\n${result.output}")
            assertTrue(
                "already in use" in result.stderr,
                "expected a port-in-use message, got:\n${result.stderr}",
            )
        }
    }

    /** Binding a non-loopback host without a token is a usage error, not a crash. */
    @Test fun non_loopback_without_token_is_a_usage_error() {
        val result = devToolsCommand().test("serve --host 0.0.0.0 --port 0")
        assertEquals(1, result.statusCode, "expected a failing exit code, got:\n${result.output}")
        assertTrue(
            "token" in result.stderr,
            "expected the missing-token usage error, got:\n${result.stderr}",
        )
    }
}
