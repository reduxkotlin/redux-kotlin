package org.reduxkotlin.devtools.monitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow
import org.reduxkotlin.devtools.ui.model.StoreEntry
import org.reduxkotlin.devtools.ui.model.StoreRegistryModel
import org.reduxkotlin.devtools.ui.model.StoreRegistryState

/**
 * A group of stores that share a `clientId` (the prefix before `"::"` in a [StoreEntry]'s id).
 * Mirrors `data.jsx`'s `clientsOf`.
 *
 * @property clientId the shared client id prefix.
 * @property label the display label (the client id for P0).
 * @property stores the stores belonging to this client.
 */
public data class ClientGroup(
    public val clientId: String,
    public val label: String,
    public val stores: List<StoreEntry>,
)

/** Groups registry [state]'s stores by the `clientId` prefix of their [StoreEntry] ref id. */
public fun clientGroups(state: StoreRegistryState): List<ClientGroup> = state.stores
    .groupBy { it.ref.id.substringBefore("::") }
    .map { (clientId, stores) -> ClientGroup(clientId, clientId, stores) }

/**
 * UI-only state for the standalone monitor. Search/regex/pause/theme live here as Compose state;
 * store selection is delegated to the [StoreRegistryModel] so the registry stays the single source
 * of truth. Construct via [rememberMonitorState].
 *
 * @param registry the aggregate store registry selection is delegated to.
 * @param registryState the latest aggregate snapshot, collected from [StoreRegistryModel.state].
 * @property endpoint the bridge endpoint the server actually bound (e.g. `ws://127.0.0.1:9090`),
 *   shown in the status cluster; empty when unknown (e.g. UI tests).
 */
public class MonitorState(
    private val registry: StoreRegistryModel,
    private val registryState: State<StoreRegistryState>,
    public val endpoint: String = "",
) {
    /** Search text across action type / payload / serialized state. */
    public var query: String by mutableStateOf("")

    /** Whether [query] is interpreted as a regex. */
    public var regex: Boolean by mutableStateOf(false)

    /** Whether capture is paused (mirrored into [MonitorIngest.paused] by the pause toggle). */
    public var paused: Boolean by mutableStateOf(false)

    /** Whether the dark theme is active. */
    public var dark: Boolean by mutableStateOf(true)

    /** The store whose row was last picked (drives "active store" in merged mode). */
    private var activeStoreId: String? by mutableStateOf(null)

    /** The current aggregate registry snapshot. */
    public val state: StoreRegistryState get() = registryState.value

    /** Clients grouped from the current snapshot. */
    public val clients: List<ClientGroup> get() = clientGroups(state)

    /** Selects exactly one store (rail click / store picker). */
    public fun focus(id: String): Unit = registry.focus(id)

    /** Selects every registered store ("All stores"). */
    public fun selectAll(): Unit = registry.selectAll()

    /** Selects exactly [ids] (checkbox toggles compute the new set and pass it here). */
    public fun select(ids: Set<String>): Unit = registry.select(ids)

    /**
     * Picks a log row: focuses [storeId] (single mode) or keeps the merged set while marking it
     * active, then selects [actionId] on that store's model so the inspector re-renders.
     */
    public fun selectRow(storeId: String, actionId: Int) {
        activeStoreId = storeId
        if (!state.merged) focus(storeId)
        registry.modelFor(storeId)?.select(actionId)
        registry.refresh()
    }

    /** Toggles a store's membership in the merged view, keeping at least one selected. */
    public fun toggle(id: String) {
        val cur = state.selectedIds
        val next = if (id in cur) (cur - id) else (cur + id)
        select(next.ifEmpty { setOf(id) })
    }

    /**
     * The "active store" the inspector renders. In merged mode it is the store of the selected
     * row; in single mode the lone focused store. Falls back to the first registered store.
     */
    public val activeStore: StoreEntry?
        get() {
            val selected = state.selectedIds
            val picked = activeStoreId?.let { id -> state.stores.firstOrNull { it.ref.id == id && id in selected } }
            val focused = state.stores.firstOrNull { it.ref.id in selected }
            return picked ?: focused ?: state.stores.firstOrNull()
        }

    /** Models a row's store for inspector lookup when a merged row is picked. */
    public fun storeById(id: String?): StoreEntry? = id?.let { sid -> state.stores.firstOrNull { it.ref.id == sid } }
}

/**
 * Remembers a [MonitorState] for [ingest] and collects the registry snapshot as Compose state.
 * [endpoint] is the bridge endpoint the server actually bound, shown in the status cluster.
 */
@Composable
public fun rememberMonitorState(ingest: MonitorIngest, endpoint: String = ""): MonitorState {
    val flow: StateFlow<StoreRegistryState> = ingest.registry.state
    val collected = flow.collectAsState()
    return remember(ingest, endpoint) { MonitorState(ingest.registry, collected, endpoint) }
}
