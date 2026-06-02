package org.reduxkotlin.devtools.remote

/**
 * Connection settings for the remote (WebSocket) DevTools output.
 *
 * @property host server host (use `10.0.2.2` from an Android emulator, or `localhost` with `adb reverse`).
 * @property port server port; the `@redux-devtools/cli` default is 8000.
 * @property secure use `wss` instead of `ws`.
 * @property startEnabled if `true`, a binder should connect this output immediately upon wiring it
 *   to a session; if `false` (default) it stays off until explicitly started.
 */
public data class RemoteConfig(
    public val host: String = "localhost",
    public val port: Int = 8000,
    public val secure: Boolean = false,
    public val startEnabled: Boolean = false,
)
