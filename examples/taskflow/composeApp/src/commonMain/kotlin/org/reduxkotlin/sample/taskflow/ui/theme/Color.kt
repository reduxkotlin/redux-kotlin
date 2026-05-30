package org.reduxkotlin.sample.taskflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// All 29 M3 color roles below come verbatim from the hi-fi spec
// (spec-assets/spec-data.js -> lightScheme / darkScheme). Each #rrggbb is
// expanded to Color(0xFFrrggbb).

/** Light [ColorScheme] for TaskFlow, filled from the hi-fi `lightScheme` tokens. */
val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF4A3FB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1C0F5B),
    secondary = Color(0xFF5D5D74),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E1F4),
    onSecondaryContainer = Color(0xFF1A1A2E),
    tertiary = Color(0xFF7E5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF31101D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5EFF7),
    surfaceContainer = Color(0xFFF0EBF3),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4CF),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFC6BEFF),
    scrim = Color(0xFF000000),
)

/** Dark [ColorScheme] for TaskFlow, filled from the hi-fi `darkScheme` tokens. */
val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC6BEFF),
    onPrimary = Color(0xFF2E1C73),
    primaryContainer = Color(0xFF41338B),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFC7C6E0),
    onSecondary = Color(0xFF2F2F45),
    secondaryContainer = Color(0xFF45455C),
    onSecondaryContainer = Color(0xFFE3E1F4),
    tertiary = Color(0xFFF0B8C7),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454E),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF4A3FB8),
    scrim = Color(0xFF000000),
)
