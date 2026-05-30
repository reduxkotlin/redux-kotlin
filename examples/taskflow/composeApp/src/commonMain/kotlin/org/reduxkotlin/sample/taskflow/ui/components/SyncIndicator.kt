package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens
import org.reduxkotlin.sample.taskflow.ui.theme.LocalSemanticColors

/**
 * The header sync indicator: a presence dot ([online] = the semantic `online` green, offline =
 * `outline`), a [pendingCount] badge on a refresh [IconButton], and the refresh action itself.
 * Surfaces the connection state and the size of the un-pushed op queue.
 *
 * Pure presentational (Rule C): immutable primitives in; [onRefresh] is remembered by the caller
 * (the screen turns it into `dispatch(Refresh)`). The component reads no store.
 *
 * @param online `true` shows the online (green) dot, `false` the offline (outline) dot.
 * @param pendingCount the number of un-pushed ops; shown as a badge when > 0.
 * @param onRefresh invoked when the refresh button is tapped (e.g. to dispatch a sync).
 * @param modifier the [Modifier] for this indicator.
 */
@Composable
public fun SyncIndicator(online: Boolean, pendingCount: Int, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (online) semantic.online else scheme.outline)
                .semantics { contentDescription = if (online) "Online" else "Offline" },
        )
        BadgedBox(
            badge = {
                if (pendingCount > 0) {
                    Badge(modifier = Modifier.semantics { contentDescription = "$pendingCount pending" }) {
                        Text(pendingCount.toString())
                    }
                }
            },
        ) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = "Refresh" },
            ) {
                // Text glyph stands in for an icon to avoid a material-icons-extended dependency.
                Text("⟳", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
