// Mirrors redux-kotlin-devtools-ui's DevToolsTab.kt — file name kept identical so the source
// layout matches the debug artifact's.
package org.reduxkotlin.devtools.ui

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
