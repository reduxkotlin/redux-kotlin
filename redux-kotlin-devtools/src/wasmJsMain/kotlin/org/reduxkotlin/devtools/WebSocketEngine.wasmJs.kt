package org.reduxkotlin.devtools

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createDevToolsHttpClient(): HttpClient = HttpClient(Js) {
    install(WebSockets)
}
