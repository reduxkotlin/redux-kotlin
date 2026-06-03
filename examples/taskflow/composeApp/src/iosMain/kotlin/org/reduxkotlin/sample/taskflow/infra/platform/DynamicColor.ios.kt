package org.reduxkotlin.sample.taskflow.infra.platform

import androidx.compose.material3.ColorScheme

/** No Material You on iOS. */
actual fun dynamicColorScheme(dark: Boolean): ColorScheme? = null
