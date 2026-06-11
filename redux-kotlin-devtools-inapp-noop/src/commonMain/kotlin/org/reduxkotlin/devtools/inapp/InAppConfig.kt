// Mirrors the inapp module's InAppConfig.kt — file name kept identical so the source layout
// matches the debug artifact's.
package org.reduxkotlin.devtools.inapp

/** No-op trigger enum (kept for API parity). */
public enum class DevToolsTrigger {
    /** Inert. */
    BUBBLE,

    /** Inert. */
    EDGE_SWIPE,
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
