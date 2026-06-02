package org.reduxkotlin.devtools.inapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.inapp.DevToolsTab
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.InAppState
import org.reduxkotlin.devtools.inapp.theme.RkTokens
import org.reduxkotlin.devtools.inapp.ui.tabs.ActionsTab
import org.reduxkotlin.devtools.inapp.ui.tabs.DiffTab
import org.reduxkotlin.devtools.inapp.ui.tabs.OutputsTab
import org.reduxkotlin.devtools.inapp.ui.tabs.PipelineTab
import org.reduxkotlin.devtools.inapp.ui.tabs.StateTab

/** The drawer: scrim + adaptive panel (bottom sheet on compact width, right panel on wide). */
@Composable
internal fun Drawer(
    open: Boolean,
    state: InAppState,
    model: InAppModel,
    onClose: () -> Unit,
    onToggleOutput: (String, Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        AnimatedVisibility(open) {
            Box(Modifier.fillMaxSize().background(RkTokens.InkSurface.copy(alpha = 0.5f)).clickable { onClose() })
        }
        val panelModifier = if (wide) {
            Modifier.fillMaxHeight().fillMaxWidth(0.42f).align(Alignment.CenterEnd)
        } else {
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = RkTokens.SheetCorner, topEnd = RkTokens.SheetCorner))
        }
        AnimatedVisibility(
            visible = open,
            enter = if (wide) slideInHorizontally { it } else slideInVertically { it },
            exit = if (wide) slideOutHorizontally { it } else slideOutVertically { it },
            modifier = panelModifier,
        ) {
            Column(Modifier.fillMaxSize().background(RkTokens.InkSurface)) {
                DrawerHeader(state, onClose)
                val tabs = DevToolsTab.entries
                TabRow(selectedTabIndex = tabs.indexOf(state.activeTab), containerColor = RkTokens.InkSurface) {
                    tabs.forEach { t ->
                        Tab(selected = t == state.activeTab, onClick = { model.setTab(t) }, text = { Text(t.name) })
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (state.activeTab) {
                        DevToolsTab.ACTIONS -> ActionsTab(state, model::setFilter) { model.select(it) }

                        DevToolsTab.STATE -> StateTab(state.selected?.state)

                        DevToolsTab.DIFF -> DiffTab(state.selected?.diff ?: emptyList())

                        DevToolsTab.PIPELINE ->
                            PipelineTab(state.structure, state.selected?.let { state.tracesById[it.actionId] })

                        DevToolsTab.OUTPUTS -> OutputsTab(state.outputs, onToggleOutput)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(state: InAppState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Redux DevTools", color = RkTokens.InkOn, style = MaterialTheme.typography.titleMedium)
        Text("  ·  ${state.actions.size} actions", color = RkTokens.InkDim, modifier = Modifier.weight(1f))
        Text("✕", color = RkTokens.InkDim, modifier = Modifier.clickable { onClose() }.padding(8.dp))
    }
}
