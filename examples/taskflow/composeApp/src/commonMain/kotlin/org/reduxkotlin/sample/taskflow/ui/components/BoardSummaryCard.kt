package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.reduxkotlin.sample.taskflow.model.BoardSummary
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The board-list entry tile for one [BoardSummary]: a left accent stripe in the board's own
 * color, the board name (Title Medium), card / done counts (Body Small), a progress bar
 * (`doneCount / cardCount`), and a last-updated line (Body Small). Tapping fires [onClick]
 * (the screen navigates into the board).
 *
 * Large (16 dp) surface, resting Level 1 — mirrors the `BoardSummaryCard` spec entry
 * (spec-data.js).
 *
 * Pure presentational (Rule C): immutable [summary] in; [onClick] is remembered by the caller.
 * Counts/`updatedAt` are pre-derived in a selector — this component does no computation.
 *
 * @param summary the board summary to render.
 * @param onClick invoked when the card is tapped (e.g. to open the board).
 * @param modifier the [Modifier] for this card.
 */
@Composable
public fun BoardSummaryCard(summary: BoardSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val progress = if (summary.cardCount > 0) {
        summary.doneCount.toFloat() / summary.cardCount.toFloat()
    } else {
        0f
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surface,
        contentColor = scheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Per-board accent stripe.
            Box(
                modifier = Modifier
                    .width(Dimens.space1)
                    .fillMaxHeight()
                    .background(Color(summary.color)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.space4),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${summary.doneCount} / ${summary.cardCount} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Updated ${summary.updatedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
