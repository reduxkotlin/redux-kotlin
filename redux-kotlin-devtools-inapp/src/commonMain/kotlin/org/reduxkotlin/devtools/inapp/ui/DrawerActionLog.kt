package org.reduxkotlin.devtools.inapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.inapp.model.ActionLogRow
import org.reduxkotlin.devtools.inapp.model.actionType
import org.reduxkotlin.devtools.inapp.model.matches
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/**
 * The drawer's action log: a filter field over a tappable, store-tagged action list. Renders the
 * shared [ActionLogRow]s (single-store or merged "All stores") with the drawer's [RkTokens] styling;
 * the store name is shown only on merged rows. Selecting a row drives the State/Diff/Pipeline tabs
 * (and, in merged mode, switches the active store) via [onSelect].
 */
@Composable
internal fun DrawerActionLog(
    rows: List<ActionLogRow>,
    filter: String,
    onFilter: (String) -> Unit,
    selectedStoreId: String?,
    selectedActionId: Int?,
    onSelect: (storeId: String, actionId: Int) -> Unit,
) {
    val shown = rows.filter { it.matches(filter, regex = false) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = filter,
            onValueChange = onFilter,
            label = { Text("Filter actions") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(shown, key = { "${it.storeId}#${it.event.actionId}" }) { row ->
                val a = row.event
                val selected = row.storeId == selectedStoreId && a.actionId == selectedActionId
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { onSelect(row.storeId, a.actionId) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        "#${a.actionId}  ${actionType(a.action)}",
                        color = if (selected) RkTokens.BlueLight else RkTokens.Orange,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val sub = if (row.merged) {
                        "${row.storeName} · ${a.timestampMillis} ms · ${a.diff.size} changes"
                    } else {
                        "${a.timestampMillis} ms · ${a.diff.size} changes"
                    }
                    Text(
                        sub,
                        color = RkTokens.InkFaint,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
