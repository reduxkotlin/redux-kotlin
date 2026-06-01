package org.reduxkotlin.sample.taskflow.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
public actual fun ApplySystemBarAppearance(lightStatusBarBackground: Boolean, lightNavigationBarBackground: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = lightStatusBarBackground
        controller.isAppearanceLightNavigationBars = lightNavigationBarBackground
    }
}
