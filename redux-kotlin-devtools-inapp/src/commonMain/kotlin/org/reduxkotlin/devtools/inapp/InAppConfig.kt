package org.reduxkotlin.devtools.inapp

/** Which built-in triggers open the drawer. */
public enum class DevToolsTrigger {
    /** A floating, draggable bubble (tap to open). Default on. */
    BUBBLE,

    /** A right-edge swipe/tab. Default on. */
    EDGE_SWIPE,
}

/** Drawer theme mode. */
public enum class DevToolsThemeMode {
    /** Follow the host app's light/dark setting. */
    SYSTEM,

    /** Always dark (the UI-kit default — best contrast for a developer tool). */
    DARK,

    /** Always light. */
    LIGHT,
}

/** Which tab is shown when the drawer first opens. */
public enum class DevToolsTab {
    /** The action log. */
    ACTIONS,

    /** The state tree. */
    STATE,

    /** The per-action diff. */
    DIFF,

    /** The pipeline map. */
    PIPELINE,

    /** The outputs list. */
    OUTPUTS,
}

/**
 * Configuration for [ReduxDevToolsHost].
 *
 * @property triggers which built-in triggers are enabled (default: bubble + edge-swipe).
 * @property startTab the tab shown when the drawer opens.
 * @property theme the drawer theme mode.
 * @property instanceId the session id to show; `null` shows the hub's sole session (or a picker if many).
 */
public data class InAppConfig(
    public val triggers: Set<DevToolsTrigger> = setOf(DevToolsTrigger.BUBBLE, DevToolsTrigger.EDGE_SWIPE),
    public val startTab: DevToolsTab = DevToolsTab.ACTIONS,
    public val theme: DevToolsThemeMode = DevToolsThemeMode.DARK,
    public val instanceId: String? = null,
)
