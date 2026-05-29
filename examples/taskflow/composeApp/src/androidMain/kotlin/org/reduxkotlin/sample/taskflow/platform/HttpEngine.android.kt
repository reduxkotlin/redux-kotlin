package org.reduxkotlin.sample.taskflow.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.android.Android

/** Android Ktor engine for Coil's network image loader. */
actual fun ktorEngineOrNull(): HttpClientEngineFactory<*>? = Android
