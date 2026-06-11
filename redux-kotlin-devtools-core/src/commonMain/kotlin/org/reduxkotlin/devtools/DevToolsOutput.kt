package org.reduxkotlin.devtools

/**
 * A toggleable consumer of a [DevToolsSession]'s feed. The in-app drawer and the remote WebSocket
 * sink are both outputs. Implementations subscribe to [DevToolsSession.events] in [start] and
 * release resources in [stop]; they must never throw into the hub.
 *
 * Outputs are registered process-globally in the [DevToolsHub]: starting or stopping one affects
 * every store whose panel lists it, not just the store it was toggled from.
 */
public interface DevToolsOutput {

    /** Stable identifier (e.g. `"remote"`, `"inapp"`); used by the UI to list and toggle outputs. */
    public val id: String

    /** Human-readable label shown in the Outputs list. */
    public val label: String

    /**
     * Whether the output is currently started (between [start] and [stop]). UIs read this to render
     * a truthful toggle. Defaults to `false` for implementations that don't track it.
     */
    public val isRunning: Boolean get() = false

    /** Begin consuming [session]. Called when the output is enabled. Must not block. */
    public fun start(session: DevToolsSession)

    /** Stop consuming and release resources. Called when the output is disabled. Must be idempotent. */
    public fun stop()
}
