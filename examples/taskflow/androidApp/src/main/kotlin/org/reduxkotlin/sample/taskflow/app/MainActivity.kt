package org.reduxkotlin.sample.taskflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.reduxkotlin.sample.taskflow.app.App
import org.reduxkotlin.sample.taskflow.infra.platform.AndroidContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.appContext = applicationContext
        // Redux DevTools (in-app drawer) is wired in the shared composeApp module (App.kt).
        enableEdgeToEdge()
        setContent { App() }
    }
}
