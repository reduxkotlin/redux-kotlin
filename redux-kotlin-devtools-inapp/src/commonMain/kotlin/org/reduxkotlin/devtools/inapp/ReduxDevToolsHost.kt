package org.reduxkotlin.devtools.inapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel
import org.reduxkotlin.devtools.inapp.theme.ReduxKotlinDevToolsTheme
import org.reduxkotlin.devtools.inapp.ui.DevToolsBubble
import org.reduxkotlin.devtools.inapp.ui.Drawer
import org.reduxkotlin.devtools.inapp.ui.EdgeTab

/** Process-visible drawer open-state, so [ReduxDevTools] can toggle it from anywhere. */
internal object DrawerState {
    var open by mutableStateOf(false)
}

/** Programmatic control of the in-app drawer. */
public object ReduxDevTools {
    /** Opens the drawer. */
    public fun open() {
        DrawerState.open = true
    }

    /** Closes the drawer. */
    public fun close() {
        DrawerState.open = false
    }
}

/**
 * Wraps the app root, rendering [content] plus the DevTools overlay (triggers + drawer) inside the
 * app's own Compose tree — no system overlay window. Resolves the session to show from the hub.
 *
 * When [InAppConfig.instanceId] is set, uses the single-session path (unchanged). Otherwise builds
 * a [StoreRegistryModel] over all sessions, driving the multi-store picker in the drawer.
 *
 * @param config drawer configuration.
 * @param content the host application content.
 */
@Composable
public fun ReduxDevToolsHost(config: InAppConfig = InAppConfig(), content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (config.instanceId != null) {
            SingleSessionOverlay(config)
        } else {
            MultiSessionOverlay(config)
        }
    }
}

@Composable
private fun SingleSessionOverlay(config: InAppConfig) {
    val session = config.instanceId?.let { DevToolsHub.session(it) }
    if (session != null) {
        val model = rememberDevToolsController(session)
        val state by model.state.collectAsState()
        DevToolsTriggers(config, state.actions.size)
        ReduxKotlinDevToolsTheme(mode = config.theme, systemDark = true) {
            Drawer(
                open = DrawerState.open,
                state = state,
                model = model,
                registry = null,
                onClose = { DrawerState.open = false },
                onToggleOutput = { id, on ->
                    val output = DevToolsHub.outputs().firstOrNull { it.id == id }
                    if (output != null) {
                        if (on) output.start(session) else output.stop()
                    }
                    model.setOutputs(
                        state.outputs.map { if (it.id == id && !it.locked) it.copy(enabled = on) else it },
                    )
                },
            )
        }
    }
}

@Composable
private fun MultiSessionOverlay(config: InAppConfig) {
    val registry = rememberStoreRegistry()
    val registryState by registry.state.collectAsState()
    val activeEntry = registryState.stores.firstOrNull { it.ref.id in registryState.selectedIds }
        ?: registryState.stores.firstOrNull()
    val activeModel = activeEntry?.let { registry.modelFor(it.ref.id) }
    if (activeEntry != null && activeModel != null) {
        val state = activeEntry.state
        val activeSession = DevToolsHub.session(activeEntry.ref.id)
        val badgeCount = if (registryState.merged) registryState.mergedRows.size else state.actions.size
        DevToolsTriggers(config, badgeCount)
        ReduxKotlinDevToolsTheme(mode = config.theme, systemDark = true) {
            Drawer(
                open = DrawerState.open,
                state = state,
                model = activeModel,
                registry = registry,
                onClose = { DrawerState.open = false },
                onToggleOutput = { id, on ->
                    val output = DevToolsHub.outputs().firstOrNull { it.id == id }
                    if (output != null && activeSession != null) {
                        if (on) output.start(activeSession) else output.stop()
                    }
                    activeModel.setOutputs(
                        state.outputs.map { if (it.id == id && !it.locked) it.copy(enabled = on) else it },
                    )
                },
            )
        }
    }
}

@Composable
private fun DevToolsTriggers(config: InAppConfig, badge: Int) {
    if (DrawerState.open) return
    if (DevToolsTrigger.BUBBLE in config.triggers) {
        Box(Modifier.fillMaxSize()) { DevToolsBubble(badge = badge) { DrawerState.open = true } }
    }
    if (DevToolsTrigger.EDGE_SWIPE in config.triggers) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            EdgeTab { DrawerState.open = true }
        }
    }
}
