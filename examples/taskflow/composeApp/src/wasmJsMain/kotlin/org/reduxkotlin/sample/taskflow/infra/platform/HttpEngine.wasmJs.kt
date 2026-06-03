package org.reduxkotlin.sample.taskflow.infra.platform

import io.ktor.client.engine.HttpClientEngineFactory

/** wasmJs has no explicit Ktor engine; Coil uses the browser fetch engine (service-loaded JS). */
actual fun ktorEngineOrNull(): HttpClientEngineFactory<*>? = null
