package org.reduxkotlin.devtools.inapp.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.inapp.DevToolsTab

/**
 * Pure, framework-free holder that reduces the session's [DevToolsEvent] stream into [InAppState].
 * No Compose dependency, so it is unit-tested directly. The Compose layer seeds it from history,
 * then forwards live events.
 *
 * @param maxActions ring bound on the retained action log (typically the session's `maxAge`).
 */
public class InAppModel(private val maxActions: Int = 50) {

    private val _state = MutableStateFlow(InAppState())

    /** Observable UI state. */
    public val state: StateFlow<InAppState> = _state

    /** Seeds the log from the session history before live collection starts. */
    public fun seed(history: List<DevToolsEvent.ActionRecorded>) {
        history.forEach { submit(it) }
    }

    /** Applies one event. Idempotent for actions (dedupes by `actionId`). */
    public fun submit(event: DevToolsEvent) {
        _state.update { reduce(it, event) }
    }

    /** User selected an action row. */
    public fun select(actionId: Int) {
        _state.update { it.copy(selectedId = actionId) }
    }

    /** User typed in the filter box. */
    public fun setFilter(text: String) {
        _state.update { it.copy(filter = text) }
    }

    /** User switched tabs. */
    public fun setTab(tab: DevToolsTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    /** Replaces the outputs list (from the hub). */
    public fun setOutputs(outputs: List<OutputRow>) {
        _state.update { it.copy(outputs = outputs) }
    }

    private fun reduce(s: InAppState, event: DevToolsEvent): InAppState = when (event) {
        is DevToolsEvent.Initialized -> s.copy(actions = emptyList(), selectedId = null, tracesById = emptyMap())

        is DevToolsEvent.ActionRecorded ->
            if (s.actions.any { it.actionId == event.actionId }) {
                s
            } else {
                // Keep ordered by actionId: reseed (history replay) can interleave with the live
                // stream, so frames may arrive out of order. Sort so the log/timeline isn't scrambled.
                s.copy(actions = (s.actions + event).sortedBy { it.actionId }.takeLast(maxActions))
            }

        is DevToolsEvent.PipelineRegistered -> s.copy(structure = event.structure)

        is DevToolsEvent.PipelineTraced -> s.copy(tracesById = s.tracesById + (event.trace.actionId to event.trace))
    }
}
