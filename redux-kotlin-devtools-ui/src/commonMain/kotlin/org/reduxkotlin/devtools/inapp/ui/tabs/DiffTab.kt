package org.reduxkotlin.devtools.inapp.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.reduxkotlin.devtools.DiffEntry
import org.reduxkotlin.devtools.DiffOp
import org.reduxkotlin.devtools.inapp.theme.RkTokens

/** The Diff tab: added/changed/removed leaf paths for the selected action. */
@Composable
public fun DiffTab(diff: List<DiffEntry>) {
    if (diff.isEmpty()) {
        Text("No changes for this action.", color = RkTokens.InkDim, modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn {
        items(diff) { e ->
            val (sym, color) = when (e.op) {
                DiffOp.ADDED -> "+" to RkTokens.Green
                DiffOp.REMOVED -> "−" to RkTokens.Red
                DiffOp.CHANGED -> "~" to RkTokens.Amber
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("$sym ${e.path}", color = color, fontFamily = FontFamily.Monospace)
                e.before?.let { Text("  - $it", color = RkTokens.Red, fontFamily = FontFamily.Monospace) }
                e.after?.let { Text("  + $it", color = RkTokens.Green, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}
