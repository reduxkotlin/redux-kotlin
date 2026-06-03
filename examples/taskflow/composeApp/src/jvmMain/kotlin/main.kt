package org.reduxkotlin.sample.taskflow

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.reduxkotlin.sample.taskflow.app.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TaskFlow") {
        App()
    }
}
