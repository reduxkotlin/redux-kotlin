package org.reduxkotlin.devtools

import io.ktor.client.HttpClient

/** Builds the platform [HttpClient] with the WebSockets plugin installed. */
internal expect fun createDevToolsHttpClient(): HttpClient
