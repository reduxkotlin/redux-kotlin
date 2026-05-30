package org.reduxkotlin.sample.taskflow.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

// MotionScheme.expressive spring tokens from the hi-fi spec
// (spec-data.js -> `springs`). Spatial springs carry a slight overshoot
// (damping < 1) — the Expressive signature; effects springs are critically
// damped (damping = 1.0) so color/opacity/elevation never bounce.
//
// These back the MaterialExpressiveTheme motionScheme when it is available, and
// also serve as the explicit fallback springs for component-level animations
// (and for TaskFlowTheme's MaterialTheme fallback path).

/**
 * The six MotionScheme.expressive spring specs, named after the spec tokens.
 * Generic so the same spec can drive `Dp`, `Float`, `Offset`, etc. animations.
 */
object TaskFlowMotion {
    /** Spatial · Fast — card press/lift, chip toggle, FAB morph. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Spatial · Default — card move/reorder, screen container-transform. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Spatial · Slow — sheet expand, large-surface enter. */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** Effects · Fast — WIP badge color crossfade, ripple. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 3800f)

    /** Effects · Default — optimistic fade, skeleton -> content. */
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1600f)

    /** Effects · Slow — theme/scheme crossfade. */
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 800f)
}
