package org.reduxkotlin.devtools.inapp.ui.tabs

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
import org.reduxkotlin.devtools.inapp.model.InAppState
import org.reduxkotlin.devtools.inapp.model.actionType
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** The Actions tab: a filter field over a tappable action log. Selecting a row drives the other tabs. */
@Composable
internal fun ActionsTab(state: InAppState, onFilter: (String) -> Unit, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.filter,
            onValueChange = onFilter,
            label = { Text("Filter actions") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.filteredActions, key = { it.actionId }) { a ->
                val selected = a.actionId == state.selected?.actionId
                Column(
                    Modifier.fillMaxWidth().clickable {
                        onSelect(
                            a.actionId,
                        )
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        "#${a.actionId}  ${actionType(a.action)}",
                        color = if (selected) RkTokens.BlueLight else RkTokens.Orange,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${a.timestampMillis} ms · ${a.diff.size} changes",
                        color = RkTokens.InkFaint,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
