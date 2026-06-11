// Mirrors the debug host root layout: Box(Modifier.fillMaxSize()) around the app content.
package org.reduxkotlin.devtools.inapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun HostFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
    }
}
