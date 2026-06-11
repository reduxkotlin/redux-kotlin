// compose-foundation publishes no klib for this target (and no debug in-app artifact exists for
// it), so the host renders the content directly.
package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable

@Composable
internal actual fun HostFrame(content: @Composable () -> Unit) {
    content()
}
