package org.reduxkotlin.sample.taskflow

import androidx.compose.ui.window.ComposeUIViewController

// PascalCase is the Compose-MP convention for the iOS view-controller factory
// (exported to Swift as `MainViewControllerKt.MainViewController()`).
@Suppress("FunctionNaming")
fun MainViewController() = ComposeUIViewController { App() }
