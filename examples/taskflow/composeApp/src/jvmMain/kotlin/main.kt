package org.reduxkotlin.sample.taskflow

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TaskFlow") {
        App()
    }
}
