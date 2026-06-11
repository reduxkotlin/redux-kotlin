package org.reduxkotlin.devtools.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.ui.model.OutputRow
import org.reduxkotlin.devtools.ui.theme.RkTokens

/**
 * The Outputs tab: one integration, multiple outputs. In-app is locked on; remote/file toggle.
 * Outputs are registered process-globally — toggling one here affects every store, not just the
 * store whose panel is showing.
 */
@Composable
public fun OutputsTab(outputs: List<OutputRow>, onToggle: (String, Boolean) -> Unit) {
    Column {
        outputs.forEach { o ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(o.label, color = RkTokens.InkOn)
                    if (o.locked) {
                        Text("in-process · always on", color = RkTokens.InkFaint)
                    } else {
                        Text(
                            if (o.enabled) "connected" else "off",
                            color = if (o.enabled) RkTokens.Green else RkTokens.InkFaint,
                        )
                    }
                }
                Switch(checked = o.enabled, enabled = !o.locked, onCheckedChange = { onToggle(o.id, it) })
            }
        }
        Text(
            "Outputs are global: toggling one affects every store. Remote streaming leaves the device " +
                "over WebSocket — off by default. The in-app drawer keeps all data in-process.",
            color = RkTokens.InkFaint,
            modifier = Modifier.padding(16.dp),
        )
    }
}
