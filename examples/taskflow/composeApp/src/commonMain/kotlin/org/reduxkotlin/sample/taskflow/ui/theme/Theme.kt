package org.reduxkotlin.sample.taskflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.reduxkotlin.sample.taskflow.core.Theme
import org.reduxkotlin.sample.taskflow.infra.platform.dynamicColorScheme
import org.reduxkotlin.sample.taskflow.ui.ApplySystemBarAppearance

/**
 * Root theme for the TaskFlow sample. Resolves the dark/light mode from the
 * app's [Theme] setting, picks the Material You dynamic scheme when [dynamic] is
 * on and available (Android 12+), otherwise the hi-fi [LightColors]/[DarkColors],
 * and installs the hi-fi typography and shapes. The app-semantic colors are
 * published via [LocalSemanticColors].
 *
 * FALLBACK NOTE: the intended root is `MaterialExpressiveTheme` +
 * `MotionScheme.expressive()`, but in this repo's Compose Multiplatform 1.11.0
 * material3 build those Expressive symbols (`MaterialExpressiveTheme`,
 * `MotionScheme`, `ExperimentalMaterial3ExpressiveApi`) are `internal` and not
 * accessible from app code (verified by compileKotlinJvm: "Cannot access ...: it
 * is internal in file"). We therefore use the stable [MaterialTheme] here and
 * drive Expressive motion explicitly from [TaskFlowMotion] (whose six spring
 * tokens mirror MotionScheme.expressive). When a material3 build exposes the
 * Expressive theme publicly, swap the [MaterialTheme] call for
 * `MaterialExpressiveTheme(colorScheme, MotionScheme.expressive(),
 * TaskFlowShapes, TaskFlowTypography)`.
 */
@Composable
fun TaskFlowTheme(theme: Theme = Theme.System, dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (theme) {
        Theme.System -> isSystemInDarkTheme()
        Theme.Light -> false
        Theme.Dark -> true
    }
    val colorScheme = (if (dynamic) dynamicColorScheme(dark) else null)
        ?: (if (dark) DarkColors else LightColors)
    val semanticColors = if (dark) DarkSemanticColors else LightSemanticColors

    // Edge-to-edge: with the root Surface drawing the theme colour all the way under the
    // status / navigation bars, the system foreground (clock, battery, gesture pill) needs
    // its contrast picked from the THEME — `enableEdgeToEdge()`'s one-shot read at activity
    // creation can be wrong (Theme.Light forced over a dark-mode system, or a runtime toggle).
    // Light theme → light bars → ask the system for dark icons; dark theme → light icons.
    ApplySystemBarAppearance(
        lightStatusBarBackground = !dark,
        lightNavigationBarBackground = !dark,
    )

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TaskFlowTypography,
            shapes = TaskFlowShapes,
            content = content,
        )
    }
}
