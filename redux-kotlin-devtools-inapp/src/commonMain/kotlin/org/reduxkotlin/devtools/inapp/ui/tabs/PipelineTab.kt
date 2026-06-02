package org.reduxkotlin.devtools.inapp.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** The Pipeline tab: the static node map, lighting nodes the selected action's [trace] traversed. */
@Composable
internal fun PipelineTab(structure: PipelineStructure?, trace: PipelineTrace?) {
    if (structure == null) {
        Text(
            "No pipeline registered. Use devToolsMiddleware / devToolsCombineReducers.",
            color = RkTokens.InkDim,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    val traceByNode = trace?.nodes?.associateBy { it.nodeId } ?: emptyMap()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        structure.nodes.forEach { node ->
            val nt = traceByNode[node.id]
            val lit = nt != null
            val color = when {
                nt?.changed == true -> RkTokens.Green
                lit -> RkTokens.BlueLight
                else -> RkTokens.InkFaint
            }
            val suffix = nt?.let { "  ·  ${it.durationNanos / 1000}µs${if (it.changed) "  changed" else ""}" } ?: ""
            Text(
                "● ${node.label}$suffix",
                color = color,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
