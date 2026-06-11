package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.OutputRow
import org.reduxkotlin.devtools.inapp.model.StoreRef
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel

/**
 * Process-global invalidation tick for output running-state. [DevToolsHub.outputsFlow] emits when
 * the registered set changes, but outputs don't self-report start/stop — so [toggleOutput] bumps
 * this after each start/stop and every store's controller re-reads [org.reduxkotlin.devtools.DevToolsOutput.isRunning],
 * keeping all Outputs panels truthful (outputs are hub-global; a toggle affects every store).
 */
internal object OutputsInvalidation {
    val tick = MutableStateFlow(0)

    fun bump() {
        tick.update { it + 1 }
    }
}

/**
 * Toggles the hub-registered output [id]: starts it on [session] when [on], stops it otherwise.
 * Outputs are hub-global, so stopping one stops it for every store — the [OutputsInvalidation] bump
 * re-syncs every store's Outputs panel to the output's actual running state.
 */
internal fun toggleOutput(id: String, on: Boolean, session: DevToolsSession?) {
    val output = DevToolsHub.outputs().firstOrNull { it.id == id } ?: return
    if (on) session?.let(output::start) else output.stop()
    OutputsInvalidation.bump()
}

/**
 * Creates an [InAppModel] bound to a [DevToolsSession], honoring the backfill contract: it seeds from
 * [DevToolsSession.history] first, then collects [DevToolsSession.events], deduping by action id.
 *
 * The model's ring bound follows [DevToolsSession.maxAge] so the drawer shows everything the session
 * recorded, and [startTab] is applied once at creation. Keyed on the [session] *instance* — if the
 * hub is reset and a session recreated under the same id, a fresh model is built for the new session
 * instead of collecting the dead one's flow.
 *
 * @param session the session to observe (resolved by `ReduxDevToolsHost` from the hub).
 * @param startTab the tab the drawer starts on ([InAppConfig.startTab]).
 * @return a remembered model whose `state` drives the drawer.
 */
@Composable
internal fun rememberDevToolsController(
    session: DevToolsSession,
    startTab: DevToolsTab = DevToolsTab.ACTIONS,
): InAppModel {
    val model = remember(session) {
        InAppModel(maxActions = session.maxAge).apply { setTab(startTab) }
    }
    LaunchedEffect(session) {
        // Backfill contract: snapshot history, then follow live — dedupe is in the model.
        model.seed(session.history())
        session.events.collect { model.submit(it) }
    }
    LaunchedEffect(session, model) {
        // Outputs are derived reactively: new registrations arrive via outputsFlow, and toggle
        // bumps re-read isRunning so the rows reflect the outputs' actual (global) state.
        combine(DevToolsHub.outputsFlow, OutputsInvalidation.tick) { outputs, _ -> outputs }
            .collect { outputs ->
                model.setOutputs(
                    outputs.map { OutputRow(it.id, it.label, enabled = it.isRunning, locked = false) } +
                        OutputRow("inapp", "In-app drawer", enabled = true, locked = true),
                )
            }
    }
    return model
}

/**
 * Builds a [StoreRegistryModel] over every session in the hub (one [InAppModel] per store), seeding
 * and following each. Drives the drawer's store-picker and "All stores" merged view.
 *
 * The session set is collected reactively from [DevToolsHub.sessionsFlow], so a store registered
 * after the drawer first composed (e.g. a per-account store created on login) is added to the
 * registry and a torn-down store is removed — the picker stays in sync without recreating the drawer.
 *
 * @param startTab the tab each store's drawer model starts on ([InAppConfig.startTab]).
 */
@Composable
internal fun rememberStoreRegistry(startTab: DevToolsTab = DevToolsTab.ACTIONS): StoreRegistryModel {
    val registry = remember { StoreRegistryModel() }
    val sessions by DevToolsHub.sessionsFlow.collectAsState()
    sessions.forEach { session ->
        key(session.id) {
            val model = rememberDevToolsController(session, startTab)
            DisposableEffect(session, model) {
                registry.put(StoreRef(session.id, session.id), model)
                onDispose { registry.remove(session.id) }
            }
            LaunchedEffect(session, model) {
                model.state.collect { registry.refresh() }
            }
        }
    }
    return registry
}
