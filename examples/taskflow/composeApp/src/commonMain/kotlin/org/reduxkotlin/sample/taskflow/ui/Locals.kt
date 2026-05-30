package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.staticCompositionLocalOf
import org.reduxkotlin.sample.taskflow.util.DefaultIdGenerator
import org.reduxkotlin.sample.taskflow.util.IdGenerator
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * CompositionLocal carrying the active [IdGenerator]. Screens read `LocalIdGenerator.current` to
 * mint ids at the dispatch site (Rule G); the default is a real [DefaultIdGenerator] and tests
 * provide a `FakeIdGenerator` for determinism.
 */
val LocalIdGenerator = staticCompositionLocalOf<IdGenerator> { DefaultIdGenerator() }

/**
 * CompositionLocal carrying the wall clock as `() -> Instant`. Screens call `LocalClock.current()`
 * when building actions so timestamps are minted at the dispatch site rather than in a reducer; the
 * default reads `Clock.System.now()` and tests can provide a fixed clock.
 */
val LocalClock = staticCompositionLocalOf<() -> Instant> { { Clock.System.now() } }
