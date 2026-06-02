package org.reduxkotlin.devtools.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createBridgeHttpClient(): HttpClient = HttpClient(Js) {
    install(WebSockets)
}
