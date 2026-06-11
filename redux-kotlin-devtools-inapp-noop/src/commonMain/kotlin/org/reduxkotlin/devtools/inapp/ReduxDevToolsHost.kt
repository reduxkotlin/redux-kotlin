// Mirrors the inapp module's ReduxDevToolsHost.kt — file name kept identical so the JVM file
// facade (`ReduxDevToolsHostKt`) matches the debug artifact's.
package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable

/** No-op programmatic control (does nothing in release). */
public object ReduxDevTools {
    /** No-op. */
    public fun open() { /* no-op */ }

    /** No-op. */
    public fun close() { /* no-op */ }
}

/**
 * No-op host: renders [content] inside the same root frame the debug host uses
 * (`Box(Modifier.fillMaxSize())`), with no overlay, no hub, no Compose-material3 — so layout
 * cannot differ between debug and release builds. On targets without compose-ui
 * (`linuxX64`/`mingwX64`, where no debug in-app artifact exists either) it renders [content]
 * directly.
 */
@Suppress("UnusedParameter")
@Composable
public fun ReduxDevToolsHost(config: InAppConfig = InAppConfig(), content: @Composable () -> Unit) {
    HostFrame(content)
}

/** Mirrors the debug host's root layout on targets where compose-foundation exists. */
@Composable
internal expect fun HostFrame(content: @Composable () -> Unit)
