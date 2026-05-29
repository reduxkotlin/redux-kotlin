package org.reduxkotlin.sample.taskflow.platform

import androidx.compose.material3.ColorScheme

/**
 * The Android Material You dynamic [ColorScheme] for the given [dark] mode when available
 * (Android 12+/API 31), or `null` on every other platform and on older Android.
 */
expect fun dynamicColorScheme(dark: Boolean): ColorScheme?
