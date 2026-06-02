package org.reduxkotlin.devtools.monitor

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.bridgeJson
import org.reduxkotlin.devtools.monitor.ui.MonitorApp
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

/**
 * Web entry point: renders the monitor UI in the browser and connects same-origin to the host's
 * `/bridge` WebSocket, decoding each text frame into a [MonitorIngest.Connection].
 */
@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
public fun main() {
    val ingest = MonitorIngest()
    connectBridge(ingest)
    ComposeViewport(document.body!!) {
        val state = rememberMonitorState(remember { ingest })
        MonitorApp(ingest, state)
    }
}

/** Opens a same-origin `/bridge` WebSocket and feeds decoded [BridgeMessage]s into [ingest]. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun connectBridge(ingest: MonitorIngest) {
    val conn = ingest.openConnection()
    val ws = WebSocket("ws://${window.location.host}/bridge")
    ws.onmessage = { event: MessageEvent ->
        (event.data as? JsString)?.toString()?.let { text ->
            runCatching { bridgeJson.decodeFromString(BridgeMessage.serializer(), text) }
                .getOrElse { e ->
                    println("connectBridge: dropped undecodable frame (${e::class.simpleName}): ${text.take(200)}")
                    null
                }
                ?.let { conn.accept(it) }
        }
    }
}
