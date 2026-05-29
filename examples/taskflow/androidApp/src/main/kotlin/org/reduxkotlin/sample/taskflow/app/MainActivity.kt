package org.reduxkotlin.sample.taskflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.reduxkotlin.sample.taskflow.App
import org.reduxkotlin.sample.taskflow.platform.AndroidContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.appContext = applicationContext
        enableEdgeToEdge()
        setContent { App() }
    }
}
