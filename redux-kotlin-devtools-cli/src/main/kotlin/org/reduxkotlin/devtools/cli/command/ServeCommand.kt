package org.reduxkotlin.devtools.cli.command

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.reduxkotlin.devtools.cli.server.startFlushing
import org.reduxkotlin.devtools.monitor.MonitorIngest
import org.reduxkotlin.devtools.monitor.MonitorServer
import org.reduxkotlin.devtools.monitor.rememberMonitorState
import org.reduxkotlin.devtools.monitor.ui.MonitorApp
import java.io.File

/** `serve` — host the bridge receiver, write per-store captures, optionally launch the GUI. */
internal class ServeCommand : CliktCommand(name = "serve") {
    private val port by option("--port", help = "port to listen on").int().default(9090)
    private val host by option("--host", help = "bind address").default("127.0.0.1")
    private val token by option("--token", help = "shared secret for non-loopback clients")
    private val out by option("--out", help = "capture output directory").default(".rk-devtools")
    private val ui by option("--ui", help = "also launch the GUI monitor").flag()

    override fun run() {
        val dir = File(out).apply { mkdirs() }
        val ingest = MonitorIngest()
        val server = MonitorServer(ingest, port = port, host = host, token = token)
        val bound = server.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startFlushing(scope, ingest, dir)
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
                    MonitorApp(ingest, rememberMonitorState(ingest))
                }
            }
        } else {
            runBlocking { awaitCancellation() }
        }
    }
}
