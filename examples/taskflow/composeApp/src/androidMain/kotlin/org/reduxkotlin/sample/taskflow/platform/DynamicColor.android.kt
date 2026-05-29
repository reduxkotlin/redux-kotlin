package org.reduxkotlin.sample.taskflow.platform

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

/** Material You dynamic color scheme on Android 12+ (API 31), else `null`. */
actual fun dynamicColorScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = AndroidContextHolder.appContext
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
