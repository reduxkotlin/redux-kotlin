package org.reduxkotlin.devtools.bridge

/**
 * Connection settings for the standalone-monitor bridge.
 *
 * @property host monitor host (default loopback; non-loopback requires [token]).
 * @property port monitor WS port (default 9090).
 * @property secure use `wss` instead of `ws`.
 * @property startEnabled connect at bind time; otherwise stay off until started.
 * @property token shared secret sent in the handshake; required by the monitor for non-loopback.
 * @property clientId stable id of this app instance (falls back to the session id if blank).
 * @property clientLabel human label for this client (device/app).
 */
public data class BridgeConfig(
    public val host: String = "127.0.0.1",
    public val port: Int = 9090,
    public val secure: Boolean = false,
    public val startEnabled: Boolean = false,
    public val token: String? = null,
    public val clientId: String = "",
    public val clientLabel: String = "redux-kotlin app",
)
