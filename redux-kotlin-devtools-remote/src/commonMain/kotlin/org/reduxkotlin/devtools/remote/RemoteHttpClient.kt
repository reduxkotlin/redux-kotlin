package org.reduxkotlin.devtools.remote

import io.ktor.client.HttpClient

/** Builds the platform [HttpClient] with the WebSockets plugin installed. */
internal expect fun createRemoteHttpClient(): HttpClient
