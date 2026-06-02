package org.reduxkotlin.devtools.monitor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.reduxkotlin.devtools.monitor.ui.MonitorApp

/** Desktop entry point: starts the bridge server on 127.0.0.1:9090 and renders the monitor UI. */
public fun main() {
    val ingest = MonitorIngest()
    val server = MonitorServer(ingest)
    server.start()
    application {
        Window(onCloseRequest = {
            server.stop()
            exitApplication()
        }, title = "Redux DevTools Monitor") {
            val state = rememberMonitorState(ingest)
            MonitorApp(ingest, state)
        }
    }
}
