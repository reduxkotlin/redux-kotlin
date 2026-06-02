package org.reduxkotlin.devtools.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.inapp.model.ActionLogRow
import org.reduxkotlin.devtools.inapp.model.actionLogRows
import org.reduxkotlin.devtools.inapp.model.actionType
import org.reduxkotlin.devtools.inapp.model.matches
import org.reduxkotlin.devtools.inapp.model.payloadPreview
import org.reduxkotlin.devtools.monitor.MonitorState

/** Visible (post-filter) rows for the current selection — used for the header count + match count. */
internal fun visibleRows(state: MonitorState): List<Pair<String, DevToolsEvent.ActionRecorded>> =
    state.state.actionLogRows(state.activeStore?.ref?.id)
        .filter { it.matches(state.query, state.regex) }
        .map { it.storeId to it.event }

/**
 * The action log: header (count + "merged by time" / "read-only") and a scrolling list of rows.
 * Selecting a row focuses its store and selects its action via [onSelect].
 */
@Composable
public fun ActionLog(state: MonitorState, colors: MonitorColors, onSelect: (storeId: String, actionId: Int) -> Unit) {
    val rows = state.state.actionLogRows(state.activeStore?.ref?.id).filter { it.matches(state.query, state.regex) }
    Column(Modifier.fillMaxSize().background(colors.logBg)) {
        LogHeader(state, rows.size, colors)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 7.dp, vertical = 6.dp)) {
            items(rows, key = { "${it.storeId}-${it.event.actionId}" }) { row ->
                ActionRow(row, state, colors, onSelect)
            }
        }
    }
}

@Composable
private fun LogHeader(state: MonitorState, shown: Int, colors: MonitorColors) {
    Row(
        Modifier.fillMaxWidth().background(colors.logBg).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "ACTION LOG",
            color = colors.faint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
        )
        Text("$shown", color = colors.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Box(Modifier.weight(1f))
        Text(
            if (state.state.merged) "merged by time" else "read-only",
            color = colors.faint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ActionRow(row: ActionLogRow, state: MonitorState, colors: MonitorColors, onSelect: (String, Int) -> Unit) {
    val selected = state.activeStore?.ref?.id == row.storeId &&
        state.activeStore?.state?.selectedId == row.event.actionId
    val type = actionType(row.event.action)
    val isInit = type.startsWith("@@")
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.sel else Color.Transparent)
            .clickable { onSelect(row.storeId, row.event.actionId) }
            .padding(start = 11.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            "${row.event.actionId}",
            color = colors.faint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            modifier = Modifier.width(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.merged) StoreChip(row.storeName, colors)
                Text(
                    type,
                    color = if (isInit) colors.dim else colors.orange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            val preview = payloadPreview(row.event)
            if (preview.isNotEmpty()) {
                Text(preview, color = colors.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
            }
        }
        val n = row.event.diff.size
        if (n > 0) {
            Text(
                "${n}Δ",
                color = colors.blue,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.5.sp,
                modifier = Modifier.clip(
                    RoundedCornerShape(5.dp),
                ).background(colors.blueSoft).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun StoreChip(name: String, colors: MonitorColors) {
    Text(
        name,
        color = colors.blue,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.5.sp,
        modifier = Modifier.clip(
            RoundedCornerShape(5.dp),
        ).background(colors.blueSoft).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
