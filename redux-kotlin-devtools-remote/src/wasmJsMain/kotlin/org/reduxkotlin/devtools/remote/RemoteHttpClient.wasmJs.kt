package org.reduxkotlin.devtools.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createRemoteHttpClient(): HttpClient = HttpClient(Js) {
    install(WebSockets)
}
