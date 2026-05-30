package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.reduxkotlin.sample.taskflow.ui.theme.ColorPair
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens
import org.reduxkotlin.sample.taskflow.ui.theme.LocalSemanticColors
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowMotion

/**
 * A sticky per-column header: the column [title] (Title Small) and a Full-shape WIP badge
 * (Label Medium) showing the live card [count] — or `count / wipLimit` when a [wipLimit] is set.
 *
 * The badge's container and on-colors animate by WIP state, sourced from the semantic WIP
 * tokens ([LocalSemanticColors]): under the limit = `wipOk` (surfaceContainerHigh /
 * onSurfaceVariant); `count == wipLimit` = `wipAtLimit` (tertiaryContainer rose); over the
 * limit = `wipOver` (errorContainer). The count and the color crossfade animate via
 * [TaskFlowMotion] effects springs (critically damped — no bounce).
 *
 * Pure presentational (Rule C): primitives in, no store, no callbacks.
 *
 * @param title the column title.
 * @param count the current card count in the column.
 * @param wipLimit the column's WIP limit, or `null` for a count-only badge (no limit set).
 * @param modifier the [Modifier] for this header.
 */
@Composable
public fun ColumnHeader(title: String, count: Int, wipLimit: Int?, modifier: Modifier = Modifier) {
    val semantic = LocalSemanticColors.current
    val target: ColorPair = when {
        wipLimit == null -> semantic.wipOk
        count > wipLimit -> semantic.wipOver
        count == wipLimit -> semantic.wipAtLimit
        else -> semantic.wipOk
    }

    val container by animateColorAsState(
        targetValue = target.container,
        animationSpec = TaskFlowMotion.effectsFast(),
        label = "wipBadgeContainer",
    )
    val onColor by animateColorAsState(
        targetValue = target.on,
        animationSpec = TaskFlowMotion.effectsFast(),
        label = "wipBadgeOn",
    )

    val badgeLabel = if (wipLimit != null) "$count of $wipLimit" else "$count"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(container)
                .padding(horizontal = Dimens.space2, vertical = 2.dp)
                .clearAndSetSemantics { contentDescription = "WIP $badgeLabel" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    fadeIn(TaskFlowMotion.effectsFast()) togetherWith fadeOut(TaskFlowMotion.effectsFast())
                },
                label = "wipBadgeCount",
            ) { value ->
                Text(
                    text = if (wipLimit != null) "$value / $wipLimit" else "$value",
                    style = MaterialTheme.typography.labelMedium,
                    color = onColor,
                )
            }
        }
    }
}
