package org.reduxkotlin.sample.taskflow.platform

import androidx.compose.material3.ColorScheme

/** No Material You on desktop. */
actual fun dynamicColorScheme(dark: Boolean): ColorScheme? = null
