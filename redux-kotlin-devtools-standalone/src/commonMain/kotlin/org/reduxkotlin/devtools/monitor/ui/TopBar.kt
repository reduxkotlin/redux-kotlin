package org.reduxkotlin.devtools.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.reduxkotlin.devtools.monitor.ClientGroup
import org.reduxkotlin.devtools.monitor.MonitorState

/**
 * The dock top bar: brand + MONITOR badge, a store picker (Client -> Store), a centered search
 * field with a regex toggle and match count, and a status + controls cluster.
 */
@Composable
public fun TopBar(
    state: MonitorState,
    colors: MonitorColors,
    matches: Int,
    onPause: () -> Unit,
    onReconnect: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onClear: () -> Unit,
    onTheme: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).background(colors.barBg)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Brand(colors)
        VerticalDivider(colors)
        StorePicker(state, colors)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SearchField(state, colors, matches)
        }
        StatusAndControls(state, colors, onPause, onReconnect, onSave, onLoad, onClear, onTheme)
    }
}

@Composable
private fun Brand(colors: MonitorColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(colors.gradient))
        Text("Redux DevTools", color = colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "MONITOR",
            color = androidx.compose.ui.graphics.Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(colors.gradient)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun VerticalDivider(colors: MonitorColors) {
    Box(Modifier.width(1.dp).height(24.dp).background(colors.line))
}

@Composable
private fun StorePicker(state: MonitorState, colors: MonitorColors) {
    var open by remember { mutableStateOf(false) }
    val active = state.activeStore
    Box {
        Row(
            modifier = Modifier.height(34.dp).clip(RoundedCornerShape(9.dp)).background(colors.hover)
                .clickable { open = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(colors.green))
            Text(
                active?.ref?.name ?: "No store",
                color = colors.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text("v", color = colors.dim, fontSize = 11.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.clients.forEach { client: ClientGroup ->
                DropdownMenuItem(
                    text = {
                        Text(
                            client.label,
                            color = colors.dim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
                client.stores.forEach { store ->
                    DropdownMenuItem(
                        text = { Text(store.ref.name, color = colors.ink, fontSize = 13.sp) },
                        onClick = {
                            state.focus(store.ref.id)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: MonitorState, colors: MonitorColors, matches: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            placeholder = { Text("Search actions, payloads, serialized state…", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (state.query.isNotEmpty()) {
            Text("$matches", color = colors.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        val regexOn = state.regex
        Text(
            ".*",
            color = if (regexOn) colors.blue else colors.faint,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                .background(if (regexOn) colors.blueSoft else androidx.compose.ui.graphics.Color.Transparent)
                .clickable { state.regex = !state.regex }.padding(4.dp),
        )
    }
}

@Composable
private fun StatusAndControls(
    state: MonitorState,
    colors: MonitorColors,
    onPause: () -> Unit,
    onReconnect: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onClear: () -> Unit,
    onTheme: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.paused) colors.amber else colors.green))
        val clients = state.clients.size
        val label = if (state.paused) "paused" else "$clients client${if (clients == 1) "" else "s"}"
        val status = if (state.endpoint.isEmpty()) label else "$label · ${state.endpoint}"
        Text(status, color = colors.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.width(2.dp))
        IconBtn(if (state.paused) "▶" else "⏸", colors, active = state.paused, onClick = onPause)
        IconBtn("↻", colors, onClick = onReconnect)
        IconBtn("↓", colors, onClick = onSave)
        IconBtn("↑", colors, onClick = onLoad)
        IconBtn("⌫", colors, onClick = onClear)
        IconBtn(if (state.dark) "☀" else "☽", colors, onClick = onTheme)
    }
}

@Composable
private fun IconBtn(glyph: String, colors: MonitorColors, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
            .background(if (active) colors.blueSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (active) colors.blue else colors.dim, fontSize = 16.sp)
    }
}
