package org.reduxkotlin.devtools.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createBridgeHttpClient(): HttpClient = HttpClient(CIO) {
    install(WebSockets)
}
