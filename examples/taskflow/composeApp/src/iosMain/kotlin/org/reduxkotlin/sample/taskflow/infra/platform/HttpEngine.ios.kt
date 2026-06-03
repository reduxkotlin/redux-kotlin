package org.reduxkotlin.sample.taskflow.infra.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/** iOS/Darwin Ktor engine for Coil's network image loader. */
actual fun ktorEngineOrNull(): HttpClientEngineFactory<*>? = Darwin
