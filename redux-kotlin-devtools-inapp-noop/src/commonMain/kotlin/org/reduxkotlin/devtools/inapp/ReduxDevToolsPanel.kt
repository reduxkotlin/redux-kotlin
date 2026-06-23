// Mirrors the inapp module's ReduxDevToolsPanel.kt — file name kept identical so the JVM file
// facade (`ReduxDevToolsPanelKt`) matches the debug artifact's (NoOpApiParityTest).
package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable
import org.reduxkotlin.devtools.ui.DevToolsTab
import org.reduxkotlin.devtools.ui.DevToolsThemeMode

/**
 * No-op embeddable panel — renders nothing in release. Signature mirrors the debug
 * [org.reduxkotlin.devtools.inapp.ReduxDevToolsPanel] exactly (no `Modifier` param, so it compiles
 * on every noop target including linux/mingw which lack compose-ui).
 */
@Suppress("UnusedParameter")
@Composable
public fun ReduxDevToolsPanel(
    instanceId: String? = null,
    startTab: DevToolsTab = DevToolsTab.ACTIONS,
    theme: DevToolsThemeMode = DevToolsThemeMode.DARK,
) { /* no-op */ }
