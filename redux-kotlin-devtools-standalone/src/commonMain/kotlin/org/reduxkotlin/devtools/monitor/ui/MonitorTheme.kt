package org.reduxkotlin.devtools.monitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The `--dt-*` palette from the `monitor/` hi-fi kit (`app.jsx` DARK_VARS / LIGHT_VARS),
 * translated to Compose [Color]s. Only the tokens the rail/log/timeline actually use are kept.
 *
 * @property bg the dock background (`--dt-bg`).
 * @property panel the center panel surface (`--dt-panel`).
 * @property barBg the top-bar surface (`--dt-bar-bg`).
 * @property railBg the rail / timeline / pipeline surface (`--dt-rail-bg`).
 * @property logBg the action-log surface (`--dt-log-bg`).
 * @property popBg popover / dropdown surface (`--dt-pop-bg`).
 * @property inputBg search-input fill (`--dt-input-bg`).
 * @property hover row hover fill (`--dt-hover`).
 * @property chip chip fill (`--dt-chip`).
 * @property line hairline border (`--dt-line`).
 * @property line2 stronger border (`--dt-line-2`).
 * @property blueLine focused-input border (`--dt-blue-line`).
 * @property selLine selected-row inset border (`--dt-sel-line`).
 * @property sel selected-row fill (`--dt-sel`).
 * @property ink primary text (`--dt-ink`).
 * @property dim secondary text (`--dt-dim`).
 * @property faint caption text (`--dt-faint`).
 * @property blue accent blue (`--dt-blue`).
 * @property blueSoft blue chip fill (`--dt-blue-soft`).
 * @property green live/success (`--dt-green`).
 * @property red error (`--dt-red`).
 * @property amber paused/changed amber (`--dt-amber`).
 * @property orange action-type orange (`--dt-orange`).
 */
public data class MonitorColors(
    public val bg: Color,
    public val panel: Color,
    public val barBg: Color,
    public val railBg: Color,
    public val logBg: Color,
    public val popBg: Color,
    public val inputBg: Color,
    public val hover: Color,
    public val chip: Color,
    public val line: Color,
    public val line2: Color,
    public val blueLine: Color,
    public val selLine: Color,
    public val sel: Color,
    public val ink: Color,
    public val dim: Color,
    public val faint: Color,
    public val blue: Color,
    public val blueSoft: Color,
    public val green: Color,
    public val red: Color,
    public val amber: Color,
    public val orange: Color,
) {
    /** The signature magenta -> orange ReduxKotlin gradient (badge, playhead, selected-row bar). */
    public val gradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(Color(0xFFC858BC), Color(0xFFF98909)),
            start = Offset.Zero,
            end = Offset.Infinite,
        )

    /** The dark + light [MonitorColors] presets from the `monitor/` kit. */
    public companion object {
        /** DARK_VARS from `app.jsx`. */
        public val Dark: MonitorColors = MonitorColors(
            bg = Color(0xFF0E1726),
            panel = Color(0xFF0B1320),
            barBg = Color(0xFF0E1726),
            railBg = Color(0xFF0A111D),
            logBg = Color(0xFF0A111D),
            popBg = Color(0xFF16203A),
            inputBg = Color(0x0DFFFFFF),
            hover = Color(0x0FFFFFFF),
            chip = Color(0x12FFFFFF),
            line = Color(0x14FFFFFF),
            line2 = Color(0x1CFFFFFF),
            blueLine = Color(0x6662A8FB),
            selLine = Color(0x4762A8FB),
            sel = Color(0x2162A8FB),
            ink = Color(0xFFE8EAF1),
            dim = Color(0xFF8A93A5),
            faint = Color(0xFF5B657A),
            blue = Color(0xFF62A8FB),
            blueSoft = Color(0x2162A8FB),
            green = Color(0xFF5FD39A),
            red = Color(0xFFFF7A8A),
            amber = Color(0xFFF9B357),
            orange = Color(0xFFF9A857),
        )

        /** LIGHT_VARS from `app.jsx`. */
        public val Light: MonitorColors = MonitorColors(
            bg = Color(0xFFFBFCFF),
            panel = Color(0xFFFFFFFF),
            barBg = Color(0xFFFFFFFF),
            railBg = Color(0xFFF2F5FA),
            logBg = Color(0xFFF7F9FC),
            popBg = Color(0xFFFFFFFF),
            inputBg = Color(0xFFEEF2F8),
            hover = Color(0x0D0E1726),
            chip = Color(0x100E1726),
            line = Color(0x1A0E1726),
            line2 = Color(0x210E1726),
            blueLine = Color(0x660464D6),
            selLine = Color(0x4D137AF9),
            sel = Color(0x17137AF9),
            ink = Color(0xFF0E1726),
            dim = Color(0xFF515A70),
            faint = Color(0xFF939BAD),
            blue = Color(0xFF0464D6),
            blueSoft = Color(0x1A137AF9),
            green = Color(0xFF1F8A4C),
            red = Color(0xFFC0354A),
            amber = Color(0xFF9A6700),
            orange = Color(0xFFB5651D),
        )
    }
}

private val DarkScheme = darkColorScheme(
    primary = MonitorColors.Dark.blue,
    secondary = Color(0xFFE07AD6),
    tertiary = MonitorColors.Dark.orange,
    background = MonitorColors.Dark.bg,
    surface = MonitorColors.Dark.panel,
    onPrimary = MonitorColors.Dark.bg,
    onBackground = MonitorColors.Dark.ink,
    onSurface = MonitorColors.Dark.ink,
    error = MonitorColors.Dark.red,
)

private val LightScheme = lightColorScheme(
    primary = MonitorColors.Light.blue,
    secondary = Color(0xFFB8419F),
    tertiary = MonitorColors.Light.orange,
    background = MonitorColors.Light.bg,
    surface = MonitorColors.Light.panel,
    onBackground = MonitorColors.Light.ink,
    onSurface = MonitorColors.Light.ink,
    error = MonitorColors.Light.red,
)

/** The active [MonitorColors] for the current [MonitorTheme] (set via [content] closure capture). */
public fun monitorColors(dark: Boolean): MonitorColors = if (dark) MonitorColors.Dark else MonitorColors.Light

/**
 * Wraps the standalone monitor in a ReduxKotlin-branded material3 theme. Pass [dark] from
 * [org.reduxkotlin.devtools.monitor.MonitorState]; read brand surfaces via [monitorColors].
 */
@Composable
public fun MonitorTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
