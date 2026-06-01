package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.runtime.Composable

@Composable
public actual fun ApplySystemBarAppearance(lightStatusBarBackground: Boolean, lightNavigationBarBackground: Boolean) {
    // iOS picks status-bar style at the UIViewController level; the sample doesn't yet wire that.
}
