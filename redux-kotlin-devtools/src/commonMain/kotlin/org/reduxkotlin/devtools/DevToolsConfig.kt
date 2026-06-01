package org.reduxkotlin.devtools

/**
 * Configuration for the [devTools] enhancer.
 *
 * @property name display name of this store instance in the monitor.
 * @property host DevTools server host (use `10.0.2.2` from an Android emulator; `localhost` with `adb reverse`).
 * @property port DevTools server port; the `@redux-devtools/cli` default is 8000.
 * @property secure whether to use `wss` instead of `ws`.
 * @property maxAge maximum number of actions retained in the history ring buffer.
 * @property instanceId stable id used by the monitor to correlate reconnects; defaults to [name].
 * @property allowlist if non-empty, only actions whose class/`toString` name matches one of these regexes are sent.
 * @property denylist actions whose class/`toString` name matches any of these regexes are never sent.
 * @property serializer override for action/state serialization; defaults to the platform tier.
 * @property logger sink for diagnostic messages (connection state, dropped actions).
 */
public data class DevToolsConfig(
    public val name: String = "redux-kotlin",
    public val host: String = "localhost",
    public val port: Int = 8000,
    public val secure: Boolean = false,
    public val maxAge: Int = 50,
    public val instanceId: String? = null,
    public val allowlist: List<String> = emptyList(),
    public val denylist: List<String> = emptyList(),
    public val serializer: ValueSerializer? = null,
    public val logger: (String) -> Unit = {},
)
