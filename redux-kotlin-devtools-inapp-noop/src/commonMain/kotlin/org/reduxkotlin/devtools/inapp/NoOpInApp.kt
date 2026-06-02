package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable

/** No-op trigger enum (kept for API parity). */
public enum class DevToolsTrigger {
    /** Inert. */
    BUBBLE,

    /** Inert. */
    EDGE_SWIPE,
}

/** No-op theme mode (kept for API parity). */
public enum class DevToolsThemeMode {
    /** Inert. */
    SYSTEM,

    /** Inert. */
    DARK,

    /** Inert. */
    LIGHT,
}

/** No-op start tab (kept for API parity). */
public enum class DevToolsTab {
    /** Inert. */
    ACTIONS,

    /** Inert. */
    STATE,

    /** Inert. */
    DIFF,

    /** Inert. */
    PIPELINE,

    /** Inert. */
    OUTPUTS,
}

/** No-op replacement of `InAppConfig`. */
public data class InAppConfig(
    /** Inert. */
    public val triggers: Set<DevToolsTrigger> = setOf(DevToolsTrigger.BUBBLE, DevToolsTrigger.EDGE_SWIPE),
    /** Inert. */
    public val startTab: DevToolsTab = DevToolsTab.ACTIONS,
    /** Inert. */
    public val theme: DevToolsThemeMode = DevToolsThemeMode.DARK,
    /** Inert. */
    public val instanceId: String? = null,
)

/** No-op programmatic control (does nothing in release). */
public object ReduxDevTools {
    /** No-op. */
    public fun open() { /* no-op */ }

    /** No-op. */
    public fun close() { /* no-op */ }
}

/** No-op host: renders [content] directly, with no overlay, no hub, no Compose-material3. */
@Suppress("UnusedParameter")
@Composable
public fun ReduxDevToolsHost(config: InAppConfig = InAppConfig(), content: @Composable () -> Unit) {
    content()
}
