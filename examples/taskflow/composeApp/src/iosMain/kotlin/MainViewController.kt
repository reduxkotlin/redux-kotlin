package org.reduxkotlin.sample.taskflow

import androidx.compose.ui.window.ComposeUIViewController
import org.reduxkotlin.sample.taskflow.app.App

// PascalCase is the Compose-MP convention for the iOS view-controller factory
// (exported to Swift as `MainViewControllerKt.MainViewController()`).
@Suppress("FunctionNaming")
fun MainViewController() = ComposeUIViewController { App() }
