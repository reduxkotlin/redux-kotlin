package org.reduxkotlin.devtools

/**
 * Transport-agnostic configuration for the [devTools] enhancer and its [DevToolsSession].
 *
 * Connection settings (host/port/secure) are **not** here — they belong to a specific output
 * (see `RemoteConfig` in `redux-kotlin-devtools-remote`). This config only governs recording.
 *
 * @property name display name of this store instance; also the default [instanceId].
 * @property instanceId stable id used to key the session in [DevToolsHub]; defaults to [name].
 * @property maxAge maximum number of actions retained in the lifted-state ring buffer.
 * @property allowlist if non-empty, only actions whose name matches one of these regexes are recorded.
 * @property denylist actions whose name matches any of these regexes are never recorded.
 * @property serializer override for action/state serialization; defaults to the platform tier.
 * @property logger sink for diagnostic messages; instrumentation never throws into the host store.
 */
public data class DevToolsConfig(
    public val name: String = "redux-kotlin",
    public val instanceId: String? = null,
    public val maxAge: Int = 50,
    public val allowlist: List<String> = emptyList(),
    public val denylist: List<String> = emptyList(),
    public val serializer: ValueSerializer? = null,
    public val logger: (String) -> Unit = {},
)
