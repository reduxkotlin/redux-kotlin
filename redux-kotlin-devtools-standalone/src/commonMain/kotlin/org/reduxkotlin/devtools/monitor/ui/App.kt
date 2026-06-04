package org.reduxkotlin.devtools.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.decodeRecording
import org.reduxkotlin.devtools.bridge.encodeRecording
import org.reduxkotlin.devtools.monitor.MonitorIngest
import org.reduxkotlin.devtools.monitor.MonitorState
import org.reduxkotlin.devtools.monitor.loadRecording
import org.reduxkotlin.devtools.monitor.saveRecording

/**
 * The standalone monitor's root composable: a winbar, the top bar, the four-panel dock
 * (rail | log | State/Diff | Pipeline), and the time-travel timeline — all driven by the live
 * [StoreRegistryModel] inside [ingest] via [state]. The State/Diff/Pipeline panels reuse the
 * `-ui` inspector tabs (`StateTab` / `DiffTab` / `PipelineTab`).
 *
 * Simplifications vs the hi-fi kit (P0): fixed panel widths (no drag splitters), tap-to-select
 * timeline (no drag-scrub), and glyph icon buttons instead of the SVG icon set.
 */
@Composable
public fun MonitorApp(ingest: MonitorIngest, state: MonitorState) {
    MonitorTheme(state.dark) {
        val colors = monitorColors(state.dark)
        val active = state.activeStore
        val title = active?.let { "${it.ref.id.substringBefore("::")} · ${it.ref.name} — Redux DevTools Monitor" }
            ?: "Redux DevTools Monitor"
        val matches = if (state.query.isBlank()) 0 else visibleRows(state).size
        Column(Modifier.fillMaxSize().background(colors.bg)) {
            WinBar(title, colors)
            TopBar(
                state = state,
                colors = colors,
                matches = matches,
                onPause = { state.paused = !state.paused },
                onReconnect = { ingest.registry.refresh() },
                onSave = {
                    val id = active?.ref?.id
                    if (id != null) {
                        ingest.recordingFor(id)?.let { (header, messages) ->
                            saveRecording("${header.storeName}.jsonl", encodeRecording(header, messages))
                        }
                    }
                },
                onLoad = {
                    loadRecording { text ->
                        val (h, msgs) = decodeRecording(text)
                        val c = ingest.openConnection()
                        c.accept(
                            BridgeMessage.Hello(
                                protocolVersion = h.protocolVersion,
                                clientId = h.clientId,
                                clientLabel = h.clientLabel,
                                storeInstanceId = h.storeInstanceId,
                                storeName = h.storeName,
                                serializerTier = h.serializerTier,
                                token = null,
                            ),
                        )
                        msgs.forEach { c.accept(it) }
                    }
                },
                onClear = {},
                onTheme = { state.dark = !state.dark },
            )
            Box(Modifier.weight(1f)) { DockBody(state, colors) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Box(Modifier.fillMaxWidth().height(92.dp)) {
                val records = active?.state?.actions ?: emptyList()
                Timeline(records, active?.state?.selectedId, colors) { id ->
                    active?.let { state.selectRow(it.ref.id, id) }
                }
            }
        }
    }
}
