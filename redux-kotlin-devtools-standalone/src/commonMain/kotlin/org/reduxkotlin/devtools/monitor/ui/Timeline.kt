package org.reduxkotlin.devtools.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.reduxkotlin.devtools.DevToolsEvent

/**
 * The bottom time-travel timeline: prev/next, "#NN / #NN", a track with ticks + a gradient
 * playhead, and a "time-travel · read-only" label. Clicking a tick (or the track region) selects.
 */
@Composable
public fun Timeline(
    records: List<DevToolsEvent.ActionRecorded>,
    selectedId: Int?,
    colors: MonitorColors,
    onSelect: (Int) -> Unit,
) {
    val n = records.size
    val idx = records.indexOfFirst { it.actionId == selectedId }.let { if (it < 0) n - 1 else it }
    Row(
        Modifier.fillMaxWidth().fillMaxHeight().background(colors.railBg).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepButtons(records, idx, colors, onSelect)
        CounterLabel(records, idx, n, colors)
        Track(records, idx, colors, Modifier.weight(1f), onSelect)
        Text("⟲ time-travel · read-only", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun StepButtons(
    records: List<DevToolsEvent.ActionRecorded>,
    idx: Int,
    colors: MonitorColors,
    onSelect: (Int) -> Unit,
) {
    val n = records.size
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepGlyph("‹", colors, enabled = idx > 0) { if (idx > 0) onSelect(records[idx - 1].actionId) }
        StepGlyph("›", colors, enabled = idx < n - 1) { if (idx < n - 1) onSelect(records[idx + 1].actionId) }
    }
}

@Composable
private fun StepGlyph(glyph: String, colors: MonitorColors, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(24.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, color = if (enabled) colors.ink else colors.faint, fontSize = 20.sp)
    }
}

@Composable
private fun CounterLabel(records: List<DevToolsEvent.ActionRecorded>, idx: Int, n: Int, colors: MonitorColors) {
    val cur = records.getOrNull(idx)?.actionId ?: 0
    Row(Modifier.width(78.dp)) {
        Text(
            "#${pad(cur)}",
            color = colors.ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
        )
        Text(
            " / #${pad((n - 1).coerceAtLeast(0))}",
            color = colors.faint,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
        )
    }
}

private fun pad(v: Int): String = v.toString().padStart(2, '0')

@Composable
private fun Track(
    records: List<DevToolsEvent.ActionRecorded>,
    idx: Int,
    colors: MonitorColors,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    val n = records.size
    BoxWithConstraints(modifier.height(44.dp)) {
        val w = maxWidth
        val pct = if (n > 1) idx.toFloat() / (n - 1) else 0f
        // baseline
        Box(
            Modifier.fillMaxWidth().height(
                3.dp,
            ).align(Alignment.Center).clip(RoundedCornerShape(3.dp)).background(colors.line2),
        )
        // filled (gradient)
        Box(
            Modifier.width(
                w * pct,
            ).height(3.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(3.dp)).background(colors.gradient),
        )
        // ticks
        records.forEachIndexed { i, rec ->
            val f = if (n > 1) i.toFloat() / (n - 1) else 0f
            val active = i <= idx
            val big = rec.diff.isNotEmpty()
            val sizeDp = if (big) 8.dp else 5.dp
            Box(
                Modifier.offset(x = w * f - sizeDp / 2).align(Alignment.Center).size(sizeDp)
                    .clip(CircleShape)
                    .background(if (active) (if (big) colors.orange else colors.blue) else colors.faint)
                    .clickable { onSelect(rec.actionId) },
            )
        }
        // playhead
        Box(
            Modifier.offset(x = w * pct - 8.dp).align(Alignment.Center).size(16.dp)
                .clip(CircleShape).background(colors.gradient),
        )
    }
}
