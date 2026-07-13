package org.reduxkotlin.devtools.cli.command

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.reduxkotlin.devtools.cli.server.flushAll
import org.reduxkotlin.devtools.cli.server.startFlushing
import org.reduxkotlin.devtools.monitor.MonitorIngest
import org.reduxkotlin.devtools.monitor.MonitorServer
import org.reduxkotlin.devtools.monitor.rememberMonitorState
import org.reduxkotlin.devtools.monitor.ui.MonitorApp
import java.io.File
import java.net.BindException

/** `serve` — host the bridge receiver, write per-store captures, optionally launch the GUI. */
internal class ServeCommand : CliktCommand(name = "serve") {
    private val port by option("--port", help = "port to listen on").int().default(9090)
    private val host by option("--host", help = "bind address").default("127.0.0.1")
    private val token by option("--token", help = "shared secret for non-loopback clients")
    private val out by option("--out", help = "capture output directory").default(DEFAULT_CAPTURE_DIR)
    private val ui by option("--ui", help = "also launch the GUI monitor").flag()

    override fun run() {
        val dir = File(out).apply { mkdirs() }
        val ingest = MonitorIngest()
        val server = MonitorServer(ingest, port = port, host = host, token = token)
        val bound = startServer(server)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startFlushing(scope, ingest, dir)
        // Final flush on Ctrl+C / SIGTERM so the captures match what was ingested.
        Runtime.getRuntime().addShutdownHook(Thread { flushAll(ingest, dir) })
        echo("serving bridge on $host:$bound  -> captures in ${dir.path}")
        if (ui) {
            application {
                Window(
                    onCloseRequest = {
                        server.stop()
                        exitApplication()
                    },
                    title = "Redux DevTools Monitor",
                ) {
                    MonitorApp(ingest, rememberMonitorState(ingest, endpoint = "ws://$host:$bound"))
                }
            }
        } else {
            runBlocking { awaitCancellation() }
        }
    }

    /** Starts [server], mapping startup failures to one-line CLI errors instead of stack traces. */
    // TooGenericExceptionCaught: the cause chain is scanned for BindException; anything else is rethrown.
    // SwallowedException: startup failures are reported as one-line CLI errors by design — the message
    // is the whole contract, and Clikt's errors take no cause.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun startServer(server: MonitorServer): Int = try {
        server.start()
    } catch (e: Exception) {
        // Ktor reports a failed bind by cancelling the engine job, so the BindException arrives
        // wrapped in a CancellationException — which is itself an IllegalStateException. Scan the
        // cause chain before treating an IllegalStateException as a configuration error.
        if (generateSequence<Throwable>(e) { it.cause }.any { it is BindException }) {
            throw PrintMessage(
                "port $port already in use on $host (--port to change)",
                statusCode = 1,
                printError = true,
            )
        }
        // e.g. non-loopback bind without a token. UsageError fixes its cause at construction, so
        // initCause() on it always throws "Can't overwrite cause" — pass the message only.
        if (e is IllegalStateException && e !is CancellationException) {
            throw UsageError(e.message ?: "invalid server configuration")
        }
        throw e
    }
}
