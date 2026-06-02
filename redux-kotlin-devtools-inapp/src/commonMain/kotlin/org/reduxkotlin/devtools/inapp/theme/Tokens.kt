package org.reduxkotlin.devtools.inapp.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Raw ReduxKotlin brand tokens (from the design system's `colors_and_type.css`). */
public object RkTokens {
    /** Web primary blue `#137AF9`. */
    public val Blue: Color = Color(0xFF137AF9)

    /** Lighter blue for dark surfaces. */
    public val BlueLight: Color = Color(0xFF62A8FB)

    /** Logo gradient warm end `#F98909`. */
    public val Orange: Color = Color(0xFFF98909)

    /** Logo gradient cool end `#C858BC`. */
    public val Magenta: Color = Color(0xFFC858BC)

    /** Heritage Redux purple `#764ABC`. */
    public val Purple: Color = Color(0xFF764ABC)

    /** Success green. */
    public val Green: Color = Color(0xFF5FD39A)

    /** Error red. */
    public val Red: Color = Color(0xFFFF7A8A)

    /** Diff "changed" amber. */
    public val Amber: Color = Color(0xFFF9B357)

    /** Dark sheet surface `#0E1726`. */
    public val InkSurface: Color = Color(0xFF0E1726)

    /** Dark elevated surface. */
    public val InkSurfaceHigh: Color = Color(0xFF16203A)

    /** Primary text on dark. */
    public val InkOn: Color = Color(0xFFE8EAF1)

    /** Dim text on dark. */
    public val InkDim: Color = Color(0xFF8A93A5)

    /** Faint text on dark. */
    public val InkFaint: Color = Color(0xFF5B657A)

    /** The signature magenta→orange gradient (use sparingly: tab indicator, bubble, edge tab). */
    public val gradient: Brush = Brush.linearGradient(
        colors = listOf(Magenta, Orange),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    /** Sheet top corner radius (M3 expressive `xl`). */
    public val SheetCorner: Dp = 28.dp
}
