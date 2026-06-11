// Mirrors core's DevToolsConfig.kt — file name kept identical so the source layout matches the
// debug artifact's.
package org.reduxkotlin.devtools

/** No-op replacement of the core `DevToolsConfig` for release builds. All fields inert. */
public data class DevToolsConfig(
    /** Inert. */
    public val name: String = "redux-kotlin",
    /** Inert. */
    public val instanceId: String? = null,
    /** Inert. */
    public val maxAge: Int = 50,
    /** Inert. */
    public val allowlist: List<String> = emptyList(),
    /** Inert. */
    public val denylist: List<String> = emptyList(),
    /** Inert. */
    public val serializer: ValueSerializer? = null,
    /** Inert. */
    public val logger: (String) -> Unit = {},
)
