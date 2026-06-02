package org.reduxkotlin.devtools.bridge

import io.ktor.client.HttpClient

/** Builds the platform [HttpClient] with the WebSockets plugin installed. */
internal expect fun createBridgeHttpClient(): HttpClient
