package org.reduxkotlin.devtools.inapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.DevToolsSession
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.OutputRow

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
