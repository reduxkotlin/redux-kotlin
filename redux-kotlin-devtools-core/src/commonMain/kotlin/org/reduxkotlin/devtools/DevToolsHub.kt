package org.reduxkotlin.devtools

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-global, debug-only registry that rendezvous the [devTools] enhancer with its outputs.
 *
 * The enhancer records into a [DevToolsSession] keyed by `instanceId`; [DevToolsOutput]s subscribe
 * to those sessions. Multi-store support falls out for free — each enhanced store is one session.
 * This object holds static state and therefore must only exist in debug builds (a release no-op
 * artifact has no hub).
 */
public object DevToolsHub {
    private val lock = SynchronizedObject()
    private val sessionsById = LinkedHashMap<String, DevToolsSession>()
    private val configsById = LinkedHashMap<String, DevToolsConfig>()
    private val registeredOutputs = ArrayList<DevToolsOutput>()
    private val sessionsFlowState = MutableStateFlow<List<DevToolsSession>>(emptyList())

    /**
     * Observable view of the active sessions. The in-app drawer's multi-store picker collects this so
     * a store registered *after* the drawer first composed — e.g. a per-account store created on login —
     * shows up without recreating the drawer. Emits a new snapshot on every create/remove/reset.
     */
    public val sessionsFlow: StateFlow<List<DevToolsSession>> = sessionsFlowState.asStateFlow()

    /**
     * Returns the existing session for the config's id, or creates and registers a new one.
     *
     * Sessions are keyed by `instanceId ?: name`. **Footgun guard:** if a session already exists for
     * the id but was created from a *different* config, two distinct stores have collided on one id
     * (most often two stores both left at the default `name = "redux-kotlin"`). Their actions would
     * interleave into one session. We log a warning so the integrator gives each store a distinct
     * `name`/`instanceId`; we still return the existing session (re-enhancing the same store with the
     * same config is the legitimate idempotent case and stays silent).
     */
    public fun createSession(config: DevToolsConfig): DevToolsSession = synchronized(lock) {
        val id = config.instanceId ?: config.name
        val existing = sessionsById[id]
        if (existing != null) {
            if (configsById[id] != config) {
                config.logger(
                    "devtools: two stores share devtools id \"$id\" — give each store a distinct " +
                        "DevToolsConfig.name or instanceId, or their actions will interleave.",
                )
            }
            return@synchronized existing
        }
        val created = DevToolsSession.create(config)
        sessionsById[id] = created
        configsById[id] = config
        publishSessions()
        created
    }

    /** Test-only: create+register a session bound to [dispatcher] so emissions drain deterministically. */
    internal fun createSessionForTest(config: DevToolsConfig, dispatcher: CoroutineDispatcher): DevToolsSession =
        synchronized(
            lock,
        ) {
            val id = config.instanceId ?: config.name
            sessionsById[id]?.let { return@synchronized it }
            val created = DevToolsSession.create(config, dispatcher)
            sessionsById[id] = created
            configsById[id] = config
            publishSessions()
            created
        }

    /** The session registered under [id], or `null`. */
    public fun session(id: String): DevToolsSession? = synchronized(lock) { sessionsById[id] }

    /** A snapshot of all active sessions (the drawer's store-picker source). */
    public fun sessions(): List<DevToolsSession> = synchronized(lock) { sessionsById.values.toList() }

    /** Closes and removes the session under [id]. Call when a store is torn down to avoid leaks. */
    public fun removeSession(id: String): Unit = synchronized(lock) {
        sessionsById.remove(id)?.close()
        configsById.remove(id)
        publishSessions()
    }

    /**
     * Registers a [DevToolsOutput]. Outputs decide for themselves whether to start (off by default).
     * Idempotent per *instance*: registering the same output twice adds it once. Distinct instances
     * may share an [DevToolsOutput.id] — outputs are per-store, and e.g. every `BridgeOutput` uses
     * the id `"bridge"` — so deduping by id would silently drop every store's output but the first.
     */
    public fun registerOutput(output: DevToolsOutput): Unit = synchronized(lock) {
        if (registeredOutputs.none { it === output }) registeredOutputs.add(output)
    }

    /** A snapshot of all registered outputs (the Outputs tab source). */
    public fun outputs(): List<DevToolsOutput> = synchronized(lock) { registeredOutputs.toList() }

    /** Clears all sessions and outputs. For tests only. */
    public fun reset(): Unit = synchronized(lock) {
        sessionsById.values.forEach { it.close() }
        sessionsById.clear()
        configsById.clear()
        registeredOutputs.clear()
        publishSessions()
    }

    /** Re-publishes the session snapshot to [sessionsFlow]. Callers must hold [lock]. */
    private fun publishSessions() {
        sessionsFlowState.value = sessionsById.values.toList()
    }
}
