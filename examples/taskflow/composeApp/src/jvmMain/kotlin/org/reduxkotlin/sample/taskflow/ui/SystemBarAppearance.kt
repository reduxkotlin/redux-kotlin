package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.Composable

@Composable
public actual fun ApplySystemBarAppearance(lightStatusBarBackground: Boolean, lightNavigationBarBackground: Boolean) {
    // Desktop windows don't expose status- or navigation-bar appearance to the app.
}
