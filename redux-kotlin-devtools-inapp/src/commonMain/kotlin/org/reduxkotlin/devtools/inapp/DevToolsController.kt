package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.OutputRow
import org.reduxkotlin.devtools.inapp.model.StoreRef
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel

/**
 * Creates an [InAppModel] bound to a [DevToolsSession], honoring the backfill contract: it seeds from
 * [DevToolsSession.history] first, then collects [DevToolsSession.events], deduping by action id.
 *
 * @param session the session to observe (resolved by `ReduxDevToolsHost` from the hub).
 * @return a remembered model whose `state` drives the drawer.
 */
@Composable
internal fun rememberDevToolsController(session: DevToolsSession): InAppModel {
    val model = remember(session.id) { InAppModel() }
    LaunchedEffect(session.id) {
        // Backfill contract: snapshot history, then follow live — dedupe is in the model.
        model.seed(session.history())
        model.setOutputs(
            DevToolsHub.outputs().map { OutputRow(it.id, it.label, enabled = false, locked = false) } +
                OutputRow("inapp", "In-app drawer", enabled = true, locked = true),
        )
        session.events.collect { model.submit(it) }
    }
    return model
}

/**
 * Builds a [StoreRegistryModel] over every session in the hub (one [InAppModel] per store), seeding
 * and following each. Drives the drawer's store-picker and "All stores" merged view.
 *
 * The session list is snapshotted once at composition time; the session set is effectively fixed
 * for the lifetime of a drawer open.
 */
@Composable
internal fun rememberStoreRegistry(): StoreRegistryModel {
    val registry = remember { StoreRegistryModel() }
    val sessions = remember { DevToolsHub.sessions() }
    sessions.forEach { session ->
        val model = rememberDevToolsController(session)
        LaunchedEffect(session.id) {
            registry.put(StoreRef(session.id, session.id), model)
            model.state.collect { registry.refresh() }
        }
    }
    return registry
}
