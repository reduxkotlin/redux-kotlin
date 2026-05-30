package org.reduxkotlin.sample.taskflow.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The M3 window size class for the available width (spec-data.js -> `breakpoints`).
 * Drives the adaptive shell: [Compact] uses a bottom NavigationBar and single paged
 * column; [Medium] / [Expanded] use a NavigationRail with more columns visible.
 */
public enum class WindowSizeClass {
    /** Width < 600 dp — phone portrait. */
    Compact,

    /** 600 dp <= width < 840 dp — foldable, tablet portrait. */
    Medium,

    /** Width >= 840 dp — tablet landscape, desktop, web. */
    Expanded,
}

/**
 * Maps an available [width] to its [WindowSizeClass] using the M3 breakpoints:
 * `< 600` Compact, `< 840` Medium, else Expanded (boundaries from [Dimens]).
 *
 * @param width the available width (e.g. `BoxWithConstraints.maxWidth`).
 * @return the [WindowSizeClass] for that width.
 */
public fun widthSizeClass(width: Dp): WindowSizeClass = when {
    width < Dimens.breakpointMedium -> WindowSizeClass.Compact
    width < Dimens.breakpointExpanded -> WindowSizeClass.Medium
    else -> WindowSizeClass.Expanded
}

/**
 * Composable helper to derive the [WindowSizeClass] for a [width], for use inside a
 * `BoxWithConstraints { val wsc = rememberWindowSize(maxWidth) }`. A thin wrapper over
 * [widthSizeClass] so screens read the breakpoint in one place.
 *
 * @param width the available width (typically `maxWidth` of a `BoxWithConstraints`).
 * @return the [WindowSizeClass] for that width.
 */
@Composable
public fun rememberWindowSize(width: Dp): WindowSizeClass = widthSizeClass(width)
