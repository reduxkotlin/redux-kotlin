package org.reduxkotlin.devtools.inapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.inapp.ui.InspectorBody
import org.reduxkotlin.devtools.inapp.ui.StorePicker
import org.reduxkotlin.devtools.ui.DevToolsTab
import org.reduxkotlin.devtools.ui.DevToolsThemeMode
import org.reduxkotlin.devtools.ui.model.actionLogRows
import org.reduxkotlin.devtools.ui.theme.ReduxKotlinDevToolsTheme

/**
 * Embeddable Redux DevTools inspector: the tabs (ACTIONS / STATE / DIFF / PIPELINE / OUTPUTS) with
 * **no** bubble, scrim, or drawer chrome — for mounting inside your own UI, e.g. a host app's debug
 * drawer. It fills the space it is given (wrap it in a sized container).
 *
 * Difference from [ReduxDevToolsHost]: the host wraps your whole app root and shows a floating
 * trigger + overlay drawer; this panel is just the inspector body and is always visible wherever you
 * place it. It does **not** touch the process-global overlay drawer state — [ReduxDevTools.open] /
 * [ReduxDevTools.close] control only the overlay drawer, never embedded panels.
 *
 * Data comes from the global [DevToolsHub] exactly as the host's drawer does, so a host and an
 * embedded panel can be shown simultaneously without conflict (session events are a broadcast flow;
 * each surface keeps its own tab/selection state).
 *
 * @param instanceId the session to show; `null` (default) shows all sessions with a store picker
 *   (matches [ReduxDevToolsHost] — a per-account store registered after first composition appears).
 * @param startTab the tab shown first.
 * @param theme the inspector theme mode.
 */
@Composable
public fun ReduxDevToolsPanel(
    instanceId: String? = null,
    startTab: DevToolsTab = DevToolsTab.ACTIONS,
    theme: DevToolsThemeMode = DevToolsThemeMode.DARK,
) {
    ReduxKotlinDevToolsTheme(mode = theme, systemDark = isSystemInDarkTheme()) {
        if (instanceId != null) SingleSessionPanel(instanceId, startTab) else MultiSessionPanel(startTab)
    }
}

@Composable
private fun SingleSessionPanel(instanceId: String, startTab: DevToolsTab) {
    val sessions by DevToolsHub.sessionsFlow.collectAsState()
    val session = sessions.firstOrNull { it.id == instanceId } ?: return
    val model = rememberDevToolsController(session, startTab)
    val state by model.state.collectAsState()
    val rows = state.actionLogRows(session.id, session.id)
    InspectorBody(
        state = state,
        model = model,
        rows = rows,
        selectedStoreId = session.id,
        selectedActionId = state.selected?.actionId,
        onSelect = { _, actionId -> model.select(actionId) },
        onToggleOutput = { id, on -> toggleOutput(id, on, session) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MultiSessionPanel(startTab: DevToolsTab) {
    val registry = rememberStoreRegistry(startTab)
    val registryState by registry.state.collectAsState()
    var activeStoreId by remember { mutableStateOf<String?>(null) }
    val activeEntry = registryState.resolveActive(activeStoreId) ?: return
    val activeModel = registry.modelFor(activeEntry.ref.id) ?: return
    val state = activeEntry.state
    val activeSession = DevToolsHub.session(activeEntry.ref.id)
    val rows = registryState.actionLogRows(activeStoreId)
    Column(Modifier.fillMaxSize()) {
        // Embedded panels render their own picker (the host puts it in the drawer header chrome,
        // which the panel omits). Hidden automatically when only one store exists.
        StorePicker(registry, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
        InspectorBody(
            state = state,
            model = activeModel,
            rows = rows,
            selectedStoreId = activeEntry.ref.id,
            selectedActionId = activeEntry.state.selected?.actionId,
            onSelect = { sid, aid ->
                activeStoreId = sid
                registry.modelFor(sid)?.select(aid)
                registry.refresh()
            },
            onToggleOutput = { id, on -> toggleOutput(id, on, activeSession) },
            modifier = Modifier.weight(1f),
        )
    }
}
