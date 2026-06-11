package org.reduxkotlin.devtools.inapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.DevToolsHub
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel
import org.reduxkotlin.devtools.inapp.model.actionLogRows
import org.reduxkotlin.devtools.inapp.theme.ReduxKotlinDevToolsTheme
import org.reduxkotlin.devtools.inapp.ui.DevToolsBubble
import org.reduxkotlin.devtools.inapp.ui.Drawer
import org.reduxkotlin.devtools.inapp.ui.EdgeTab
import kotlin.math.roundToInt

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
 * app's own Compose tree — no system overlay window. Resolves the session to show from the hub
 * reactively, so a session registered after first composition still appears.
 *
 * When [InAppConfig.instanceId] is set, uses the single-session path (unchanged). Otherwise builds
 * a [StoreRegistryModel] over all sessions, driving the multi-store picker in the drawer.
 *
 * The drawer open-state is process-global (intended): on multi-window desktop every window hosting
 * a [ReduxDevToolsHost] shares one open/closed flag, so [ReduxDevTools.open] affects them all.
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
    // Resolve from the reactive flow (not a one-shot lookup) so a session registered after first
    // composition still shows up.
    val sessions by DevToolsHub.sessionsFlow.collectAsState()
    val session = sessions.firstOrNull { it.id == config.instanceId }
    if (session != null) {
        val model = rememberDevToolsController(session, config.startTab)
        val state by model.state.collectAsState()
        DevToolsTriggers(config, state.actions.size)
        val rows = state.actionLogRows(session.id, session.id)
        ReduxKotlinDevToolsTheme(mode = config.theme, systemDark = isSystemInDarkTheme()) {
            Drawer(
                open = DrawerState.open,
                state = state,
                model = model,
                registry = null,
                rows = rows,
                selectedStoreId = session.id,
                selectedActionId = state.selected?.actionId,
                onSelect = { _, actionId -> model.select(actionId) },
                onClose = { DrawerState.open = false },
                onToggleOutput = { id, on -> toggleOutput(id, on, session) },
            )
        }
    }
}

@Composable
private fun MultiSessionOverlay(config: InAppConfig) {
    val registry = rememberStoreRegistry(config.startTab)
    val registryState by registry.state.collectAsState()
    var activeStoreId by remember { mutableStateOf<String?>(null) }
    val activeEntry = registryState.resolveActive(activeStoreId)
    val activeModel = activeEntry?.let { registry.modelFor(it.ref.id) }
    if (activeEntry != null && activeModel != null) {
        val state = activeEntry.state
        val activeSession = DevToolsHub.session(activeEntry.ref.id)
        val badgeCount = if (registryState.merged) registryState.mergedRows.size else state.actions.size
        val rows = registryState.actionLogRows(activeStoreId)
        DevToolsTriggers(config, badgeCount)
        ReduxKotlinDevToolsTheme(mode = config.theme, systemDark = isSystemInDarkTheme()) {
            Drawer(
                open = DrawerState.open,
                state = state,
                model = activeModel,
                registry = registry,
                rows = rows,
                selectedStoreId = activeEntry.ref.id,
                selectedActionId = activeEntry.state.selected?.actionId,
                onSelect = { sid, aid ->
                    activeStoreId = sid
                    registry.modelFor(sid)?.select(aid)
                    registry.refresh()
                },
                onClose = { DrawerState.open = false },
                onToggleOutput = { id, on -> toggleOutput(id, on, activeSession) },
            )
        }
    }
}

@Composable
private fun DevToolsTriggers(config: InAppConfig, badge: Int) {
    // Bubble position is hoisted above the early-return below: when the drawer opens the bubble
    // leaves the composition, and remembering the offset here keeps the dragged position alive.
    // The default offset is density-scaled so it lands in the same spot on every screen.
    val density = LocalDensity.current
    var bubbleOffset by remember {
        mutableStateOf(with(density) { IntOffset(40.dp.roundToPx(), 240.dp.roundToPx()) })
    }
    if (DrawerState.open) return
    if (DevToolsTrigger.BUBBLE in config.triggers) {
        Box(Modifier.fillMaxSize()) {
            DevToolsBubble(
                badge = badge,
                offset = bubbleOffset,
                onDrag = { drag ->
                    bubbleOffset = IntOffset(
                        (bubbleOffset.x + drag.x).roundToInt(),
                        (bubbleOffset.y + drag.y).roundToInt(),
                    )
                },
                onOpen = { DrawerState.open = true },
            )
        }
    }
    if (DevToolsTrigger.EDGE_SWIPE in config.triggers) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            EdgeTab { DrawerState.open = true }
        }
    }
}
