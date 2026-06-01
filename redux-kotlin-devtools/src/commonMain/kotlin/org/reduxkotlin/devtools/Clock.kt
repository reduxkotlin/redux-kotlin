package org.reduxkotlin.devtools

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Returns the current epoch time in milliseconds. Injected for deterministic tests. */
internal typealias EpochMillis = () -> Long

@OptIn(ExperimentalTime::class)
internal val systemClock: EpochMillis = { Clock.System.now().toEpochMilliseconds() }
