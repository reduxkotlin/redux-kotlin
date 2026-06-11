package org.reduxkotlin.devtools.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.reduxkotlin.devtools.DevToolsOutput
import org.reduxkotlin.devtools.DevToolsSession

/**
 * Streams a store's [DevToolsSession] feed to the standalone monitor over the bridge protocol.
 * Per-store (one connection per store). Off by default — localhost-bound, a token required for
 * non-loopback (see [BridgeConfig]). Debug-only: wire as `debugImplementation`, never ship in release.
 *
 * @param config connection + identity settings.
 * @param logger diagnostic sink.
 */
public class BridgeOutput(private val config: BridgeConfig, private val logger: (String) -> Unit = {}) :
    DevToolsOutput {

    override val id: String = "bridge"
    override val label: String = "Standalone monitor (bridge)"

    /** Mirrors [BridgeConfig.startEnabled]; a binder consults it to auto-connect at registration. */
    public val startEnabled: Boolean get() = config.startEnabled

    private var scope: CoroutineScope? = null
    private var connection: BridgeConnection? = null

    /**
     * Whether the output has been started (and not yet stopped). `true` does **not** imply a live
     * connection — the connect loop may still be dialing or waiting out a reconnect backoff.
     */
    public val isRunning: Boolean get() = scope != null

    /**
     * Subscribes to [session] and starts the bridge connect loop.
     *
     * Not thread-safe; call [start]/[stop] from a single thread (e.g. the UI thread).
     */
    override fun start(session: DevToolsSession) {
        if (isRunning) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        val conn = BridgeConnection(config, session, logger).also { connection = it }
        conn.start(s)
        session.events
            .onEach { event -> runCatching { conn.enqueue(toWire(event)) } }
            .launchIn(s)
    }

    /**
     * Stops the connection and cancels the subscription.
     *
     * Not thread-safe; call [start]/[stop] from a single thread (e.g. the UI thread).
     */
    override fun stop() {
        connection?.stop()
        connection = null
        scope?.cancel()
        scope = null
    }
}
