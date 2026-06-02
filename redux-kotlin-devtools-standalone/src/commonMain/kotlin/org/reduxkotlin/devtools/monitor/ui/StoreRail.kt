package org.reduxkotlin.devtools.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import org.reduxkotlin.devtools.inapp.model.StoreEntry
import org.reduxkotlin.devtools.monitor.ClientGroup
import org.reduxkotlin.devtools.monitor.MonitorState

/** The left rail: "CLIENTS & STORES", an "All stores" row, then per-client store rows + footer. */
@Composable
public fun StoreRail(state: MonitorState, colors: MonitorColors) {
    val stores = state.state.stores
    val selected = state.state.selectedIds
    val allOn = selected.size == stores.size && stores.isNotEmpty()
    Column(Modifier.fillMaxSize().background(colors.railBg)) {
        SectionLabel("CLIENTS & STORES", colors, Modifier.padding(start = 14.dp, top = 13.dp, bottom = 8.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            item { AllStoresRow(allOn, stores.sumOf { it.state.actions.size }, colors) { state.selectAll() } }
            state.clients.forEach { client ->
                item { ClientHeader(client, colors) }
                items(client.stores, key = { it.ref.id }) { store ->
                    StoreRow(store, state, colors)
                }
            }
        }
        Footer(state.clients.size, stores.size, stores.sumOf { it.state.actions.size }, colors)
    }
}

@Composable
private fun SectionLabel(text: String, colors: MonitorColors, modifier: Modifier = Modifier) {
    Text(
        text,
        color = colors.faint,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        modifier = modifier,
    )
}

@Composable
private fun AllStoresRow(allOn: Boolean, total: Int, colors: MonitorColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(11.dp))
            .background(if (allOn) colors.sel else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("≣", color = if (allOn) colors.blue else colors.dim, fontSize = 14.sp)
        Text(
            "All stores",
            color = if (allOn) colors.ink else colors.dim,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text("$total", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun ClientHeader(client: ClientGroup, colors: MonitorColors) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(colors.green))
        Text(
            client.label,
            color = colors.dim,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StoreRow(store: StoreEntry, state: MonitorState, colors: MonitorColors) {
    val isChecked = store.ref.id in state.state.selectedIds
    val isActive = state.activeStore?.ref?.id == store.ref.id
    Row(
        Modifier.fillMaxWidth().padding(start = 9.dp, bottom = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (isActive) colors.sel else Color.Transparent)
            .clickable { state.focus(store.ref.id) }
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Checkbox(isChecked, colors.blue, colors.line2) { state.toggle(store.ref.id) }
        Box(Modifier.size(7.dp).clip(CircleShape).background(colors.blue))
        Text(
            store.ref.name,
            color = if (isActive) colors.ink else colors.dim,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text("${store.state.actions.size}", color = colors.faint, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun Checkbox(checked: Boolean, accent: Color, border: Color, onToggle: () -> Unit) {
    Box(
        Modifier.size(16.dp).clip(RoundedCornerShape(5.dp))
            .background(if (checked) accent else Color.Transparent)
            .border(1.5.dp, if (checked) accent else border, RoundedCornerShape(5.dp))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun Footer(clients: Int, stores: Int, actions: Int, colors: MonitorColors) {
    Column(
        Modifier.fillMaxWidth().background(colors.railBg).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("$actions actions retained", color = colors.dim, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
        Text(
            "$clients clients · $stores stores",
            color = colors.faint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
        )
    }
}
