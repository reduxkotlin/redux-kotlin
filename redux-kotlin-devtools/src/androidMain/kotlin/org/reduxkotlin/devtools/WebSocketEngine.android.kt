package org.reduxkotlin.devtools

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createDevToolsHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
}
