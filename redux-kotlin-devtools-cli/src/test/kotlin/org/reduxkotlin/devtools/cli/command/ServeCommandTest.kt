package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.testing.test
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** The verbatim error a bare Ubuntu box throws for `--ui`; it must name the lib and the fix. */
    @Test fun missing_x11_library_on_linux_names_the_library_and_the_packages() {
        val error = UnsatisfiedLinkError(
            "/home/u/rk/lib/runtime/lib/libawt_xawt.so: libXtst.so.6: " +
                "cannot open shared object file: No such file or directory",
        )
        val hint = missingNativeLibraryHint(error, "Linux")
        assertNotNull(hint, "expected a hint for a missing X11 library")
        assertTrue("libXtst.so.6" in hint, "hint must name the missing library, got:\n$hint")
        assertTrue("apt install" in hint, "hint must say how to install it, got:\n$hint")
    }

    /** Anything that is not a missing system library keeps its stack trace. */
    @Test fun unrelated_link_errors_are_not_rewritten() {
        val unrelated = UnsatisfiedLinkError("no skiko in java.library.path")
        assertNull(missingNativeLibraryHint(unrelated, "Linux"))
        val macOs = UnsatisfiedLinkError("libXtst.so.6: cannot open shared object file")
        assertNull(missingNativeLibraryHint(macOs, "Mac OS X"))
    }
}
