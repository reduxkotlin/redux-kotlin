package org.reduxkotlin.devtools.ui

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
