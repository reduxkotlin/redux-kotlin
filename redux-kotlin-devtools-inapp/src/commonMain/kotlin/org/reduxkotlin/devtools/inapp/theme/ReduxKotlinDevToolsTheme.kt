package org.reduxkotlin.devtools.inapp.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import org.reduxkotlin.devtools.inapp.DevToolsThemeMode

private val DarkColors = darkColorScheme(
    primary = RkTokens.BlueLight,
    secondary = RkTokens.Magenta,
    tertiary = RkTokens.Orange,
    background = RkTokens.InkSurface,
    surface = RkTokens.InkSurface,
    surfaceVariant = RkTokens.InkSurfaceHigh,
    onPrimary = RkTokens.InkSurface,
    onBackground = RkTokens.InkOn,
    onSurface = RkTokens.InkOn,
    error = RkTokens.Red,
)

private val LightColors = lightColorScheme(
    primary = RkTokens.Blue,
    secondary = RkTokens.Magenta,
    tertiary = RkTokens.Orange,
)

/**
 * Wraps DevTools content in a ReduxKotlin-branded [MaterialTheme]. Defaults to dark — the UI-kit
 * default and best contrast for a developer overlay — or follows the host per [mode].
 *
 * @param mode theme mode from `InAppConfig`.
 * @param systemDark whether the host is currently in dark mode (used when [mode] is SYSTEM).
 * @param content the themed content.
 */
@Composable
public fun ReduxKotlinDevToolsTheme(mode: DevToolsThemeMode, systemDark: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        DevToolsThemeMode.DARK -> true
        DevToolsThemeMode.LIGHT -> false
        DevToolsThemeMode.SYSTEM -> systemDark
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
