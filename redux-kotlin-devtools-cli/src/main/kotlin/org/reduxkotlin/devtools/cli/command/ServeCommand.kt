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
    @Suppress("TooGenericExceptionCaught") // cause chains are scanned for BindException, rest rethrown
    private fun startServer(server: MonitorServer): Int = try {
        server.start()
    } catch (e: IllegalStateException) {
        // e.g. non-loopback bind without a token
        throw UsageError(e.message ?: "invalid server configuration").initCause(e)
    } catch (e: Exception) {
        if (generateSequence<Throwable>(e) { it.cause }.any { it is BindException }) {
            throw PrintMessage(
                "port $port already in use on $host (--port to change)",
                statusCode = 1,
                printError = true,
            )
        }
        throw e
    }
}
