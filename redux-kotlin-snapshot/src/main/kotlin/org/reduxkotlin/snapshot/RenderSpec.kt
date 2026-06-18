package org.reduxkotlin.snapshot

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

/**
 * A fully-resolved render request: a logical [widthDp] x [heightDp] surface at [density]
 * (output pixels = dp * density), rendering [content].
 */
public class RenderSpec(
    /** Logical width in dp. */
    public val widthDp: Int,
    /** Logical height in dp. */
    public val heightDp: Int,
    /** Pixel density; output px = dp * density. */
    public val density: Float,
    /** The composable to render into the surface. */
    public val content: @Composable () -> Unit,
) {
    /** Output pixel width (`widthDp * density`, rounded). */
    public val widthPx: Int get() = (widthDp * density).roundToInt()

    /** Output pixel height (`heightDp * density`, rounded). */
    public val heightPx: Int get() = (heightDp * density).roundToInt()
}
