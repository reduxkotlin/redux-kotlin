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
 * @param config drawer configuration.
 * @param content the host application content.
 */
@Composable
public fun ReduxDevToolsHost(config: InAppConfig = InAppConfig(), content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()

        val session = config.instanceId?.let { DevToolsHub.session(it) } ?: DevToolsHub.sessions().firstOrNull()
        if (session != null) {
            val model = rememberDevToolsController(session)
            val state by model.state.collectAsState()

            if (!DrawerState.open) {
                if (DevToolsTrigger.BUBBLE in config.triggers) {
                    Box(
                        Modifier.fillMaxSize(),
                    ) { DevToolsBubble(badge = state.actions.size) { DrawerState.open = true } }
                }
                if (DevToolsTrigger.EDGE_SWIPE in config.triggers) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterEnd,
                    ) { EdgeTab { DrawerState.open = true } }
                }
            }

            ReduxKotlinDevToolsTheme(mode = config.theme, systemDark = true) {
                Drawer(
                    open = DrawerState.open,
                    state = state,
                    model = model,
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
}
