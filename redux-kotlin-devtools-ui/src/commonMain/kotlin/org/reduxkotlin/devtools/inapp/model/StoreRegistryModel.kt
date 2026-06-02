package org.reduxkotlin.devtools.inapp.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.reduxkotlin.devtools.DevToolsEvent

/** Stable identity of a store in the registry. */
public data class StoreRef(
    /** Unique key (in-app: `instanceId`; standalone: `clientId + storeInstanceId`). */
    public val id: String,
    /** Display name shown on rows/badges. */
    public val name: String,
)

/** One row of the merged action log, tagged with the store it came from. */
public data class StoreActionRow(
    /** The store's id. */
    public val storeId: String,
    /** The store's display name (for the badge). */
    public val storeName: String,
    /** The recorded action. */
    public val event: DevToolsEvent.ActionRecorded,
)

/** A registered store and its current snapshot. */
public data class StoreEntry(
    /** The store's identity. */
    public val ref: StoreRef,
    /** The store's UI state. */
    public val state: InAppState,
)

/** Immutable aggregate view across all registered stores. */
public data class StoreRegistryState(
    /** All registered stores, in registration order. */
    public val stores: List<StoreEntry> = emptyList(),
    /** Ids currently included in the view. */
    public val selectedIds: Set<String> = emptySet(),
) {
    /** `true` when more than one store is selected (drives badges + "merged by time"). */
    public val merged: Boolean get() = selectedIds.size > 1

    /** Selected stores' actions interleaved by `timestampMillis`, each tagged with its store. */
    public val mergedRows: List<StoreActionRow>
        get() = stores
            .filter { it.ref.id in selectedIds }
            .flatMap { e -> e.state.actions.map { StoreActionRow(e.ref.id, e.ref.name, it) } }
            .sortedWith(compareBy({ it.event.timestampMillis }, { it.storeId }, { it.event.actionId }))
}

/**
 * Aggregates many [InAppModel]s into one selectable/filterable/merged view. Pure and Compose-free,
 * so it is unit-tested directly and reused by both the in-app drawer (keyed by `instanceId`) and the
 * standalone monitor (keyed by `clientId + storeInstanceId`). The host registers/updates stores; the
 * model recomputes the aggregate [state].
 */
public class StoreRegistryModel {
    private data class Slot(val ref: StoreRef, val model: InAppModel)

    private val slots = LinkedHashMap<String, Slot>()
    private val _state = MutableStateFlow(StoreRegistryState())

    /** Observable aggregate view. */
    public val state: StateFlow<StoreRegistryState> = _state

    /** Registers (or replaces) the store [ref] backed by [model]; auto-selects the first store. */
    public fun put(ref: StoreRef, model: InAppModel) {
        slots[ref.id] = Slot(ref, model)
        if (_state.value.selectedIds.isEmpty()) _state.value = _state.value.copy(selectedIds = setOf(ref.id))
        recompute()
    }

    /** Removes a store. */
    public fun remove(id: String) {
        slots.remove(id)
        _state.value = _state.value.copy(selectedIds = _state.value.selectedIds - id)
        if (_state.value.selectedIds.isEmpty()) slots.keys.firstOrNull()?.let { focus(it) }
        recompute()
    }

    /** Selects exactly the given store ids (filter to a subset). */
    public fun select(ids: Set<String>) {
        _state.value = _state.value.copy(selectedIds = ids.ifEmpty { slots.keys.take(1).toSet() })
    }

    /** Selects exactly one store (solo view). */
    public fun focus(id: String): Unit = select(setOf(id))

    /** Selects all registered stores ("view all"). */
    public fun selectAll(): Unit = select(slots.keys.toSet())

    /** Returns the [InAppModel] for [id], or `null` if not registered. */
    public fun modelFor(id: String): InAppModel? = slots[id]?.model

    /** Re-reads child models' current state into the aggregate (host calls on any child change). */
    public fun refresh(): Unit = recompute()

    private fun recompute() {
        _state.value = _state.value.copy(
            stores = slots.values.map { StoreEntry(it.ref, it.model.state.value) },
        )
    }
}
