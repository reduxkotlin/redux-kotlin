package org.reduxkotlin.sample.taskflow.infra.platform

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * The platform Ktor [HttpClientEngineFactory] for Coil's network image loader, or `null` on
 * wasmJs where Coil falls back to the browser fetch engine (Ktor service-loads its JS engine).
 */
expect fun ktorEngineOrNull(): HttpClientEngineFactory<*>?
