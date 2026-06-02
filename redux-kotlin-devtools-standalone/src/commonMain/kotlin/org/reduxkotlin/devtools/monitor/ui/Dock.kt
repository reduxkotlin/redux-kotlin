package org.reduxkotlin.devtools.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.reduxkotlin.devtools.inapp.ui.tabs.DiffTab
import org.reduxkotlin.devtools.inapp.ui.tabs.PipelineTab
import org.reduxkotlin.devtools.inapp.ui.tabs.StateTab
import org.reduxkotlin.devtools.monitor.MonitorState

private val RAIL_WIDTH = 208.dp
private val LOG_WIDTH = 312.dp
private val PIPELINE_WIDTH = 312.dp

/** The winbar: a thin bar with three traffic-light circles + a title. */
@Composable
internal fun WinBar(title: String, colors: MonitorColors) {
    Row(
        Modifier.fillMaxWidth().height(28.dp).background(colors.barBg).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFFF5F57)))
        Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFFEBC2E)))
        Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFF28C840)))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(title, color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Box(Modifier.width(33.dp))
    }
}

/** A panel header: an uppercase mono label + optional right slot. */
@Composable
internal fun PanelHead(label: String, colors: MonitorColors) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(label, color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.4.sp)
    }
}

/**
 * The center column: State over Diff, split ~56/44, each REUSING the `-ui` tab composables.
 */
@Composable
private fun CenterColumn(state: MonitorState, colors: MonitorColors, modifier: Modifier) {
    val store = state.activeStore
    val selected = store?.state?.selected
    Column(modifier.fillMaxHeight().background(colors.panel)) {
        Column(Modifier.weight(0.56f)) {
            PanelHead("STATE", colors)
            Box(Modifier.weight(1f)) { StateTab(selected?.state) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Column(Modifier.weight(0.44f)) {
            PanelHead("DIFF", colors)
            Box(Modifier.weight(1f)) { DiffTab(selected?.diff ?: emptyList()) }
        }
    }
}

/** The right pipeline panel, REUSING the `-ui` [PipelineTab]. */
@Composable
private fun PipelinePanel(state: MonitorState, colors: MonitorColors) {
    val store = state.activeStore
    val selectedId = store?.state?.selectedId
    Column(Modifier.width(PIPELINE_WIDTH).fillMaxHeight().background(colors.railBg)) {
        PanelHead("PIPELINE", colors)
        Box(Modifier.weight(1f)) {
            PipelineTab(store?.state?.structure, selectedId?.let { store?.state?.tracesById?.get(it) })
        }
    }
}

/** Assembles the IDE-dock body: rail | log | center | pipeline. */
@Composable
internal fun DockBody(state: MonitorState, colors: MonitorColors) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(RAIL_WIDTH).fillMaxHeight()) { StoreRail(state, colors) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
        Box(Modifier.width(LOG_WIDTH).fillMaxHeight()) {
            ActionLog(state, colors) { storeId, actionId -> state.selectRow(storeId, actionId) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
        CenterColumn(state, colors, Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
        PipelinePanel(state, colors)
    }
}
