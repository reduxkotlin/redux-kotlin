package org.reduxkotlin.devtools.inapp

import org.reduxkotlin.devtools.ui.DevToolsTab
import org.reduxkotlin.devtools.ui.DevToolsThemeMode

/** Which built-in triggers open the drawer. */
public enum class DevToolsTrigger {
    /** A floating, draggable bubble (tap to open). Default on. */
    BUBBLE,

    /** A right-edge tab; tap it or swipe it leftward to open. Default on. */
    EDGE_SWIPE,
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
