package org.reduxkotlin.sample.taskflow.feature.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A connected two-button group for moving a card to the adjacent column: `◂ prev` and `next ▸`.
 * The move is optimistic at the call site — this component just surfaces the two edges and an
 * in-flight indicator.
 *
 * The spec calls for an Expressive connected `ButtonGroup` (`secondaryContainer`); that API is
 * internal in this material3 build, so this is the documented fallback: a connected
 * [SingleChoiceSegmentedButtonRow] of two [SegmentedButton]s (per spec-data.js —
 * `ButtonGroup -> SegmentedButtons`).
 *
 * Pure presentational (Rule C): immutable primitives in; both callbacks remembered by the caller.
 *
 * @param canPrev `true` enables the previous-column button (disabled at the left edge).
 * @param canNext `true` enables the next-column button (disabled at the right edge).
 * @param inFlight `true` while a move request is in flight — shows a small progress indicator and
 *  disables both buttons.
 * @param onPrev invoked when the previous-column button is tapped.
 * @param onNext invoked when the next-column button is tapped.
 * @param modifier the [Modifier] for this group.
 */
@Composable
public fun MoveToGroup(
    canPrev: Boolean,
    canNext: Boolean,
    inFlight: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SegmentedButtonDefaults.colors(
        inactiveContainerColor = scheme.secondaryContainer,
        inactiveContentColor = scheme.onSecondaryContainer,
        disabledInactiveContainerColor = scheme.surfaceContainer,
        disabledInactiveContentColor = scheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = false,
            onClick = onPrev,
            enabled = canPrev && !inFlight,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = colors,
            modifier = Modifier.semantics { contentDescription = "Move to previous column" },
        ) {
            if (inFlight) InFlightDot() else Text("◂ prev", style = MaterialTheme.typography.labelLarge)
        }
        SegmentedButton(
            selected = false,
            onClick = onNext,
            enabled = canNext && !inFlight,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = colors,
            modifier = Modifier.semantics { contentDescription = "Move to next column" },
        ) {
            if (inFlight) InFlightDot() else Text("next ▸", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A small in-flight progress indicator shown inside a segment while a move is pending. */
@Composable
private fun InFlightDot() {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
    }
}
