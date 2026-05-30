package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.Attachment
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens
import org.reduxkotlin.sample.taskflow.ui.theme.LocalSemanticColors
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowMotion

/**
 * The atom of the board: one [Card] rendered as a Large (16 dp) surface — resting
 * Level 1 ([surfaceContainerLowest][androidx.compose.material3.ColorScheme.surfaceContainerLowest]
 * on the board's `surfaceContainer`). Renders the card title (Title Medium), a wrapping
 * row of [LabelChip]s, the first image [Attachment] (a fixed-size [AsyncImage] thumbnail),
 * and a footer with Body Small meta plus the [assignee]'s [Avatar].
 *
 * Visual states (the dead "dragging" state is intentionally omitted):
 * - pressed — shape morphs toward Large-increased (20 dp) and elevation lifts to Level 2,
 *   both via [TaskFlowMotion] springs;
 * - [isSelected] — a 2 dp primary outline;
 * - [isOptimistic] — 60% alpha plus a small "Saving" chip in the semantic `saving` color.
 *
 * Pure presentational (Rule C): it takes only immutable data + a remembered [onClick]
 * callback, reads no store, and runs no business logic. [onClick] fires on tap.
 *
 * @param card the card to render.
 * @param isOptimistic `true` while an optimistic op for this card is in flight (faded + chip).
 * @param assignee the resolved assignee summary for the footer avatar, or `null` if unassigned.
 * @param onClick invoked when the card is tapped (e.g. to open card detail).
 * @param modifier the [Modifier] for this card.
 * @param isSelected `true` draws a 2 dp primary selection outline.
 */
@Composable
public fun KanbanCard(
    card: Card,
    isOptimistic: Boolean,
    assignee: AccountSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Pressed -> shape-morph (16 -> 20 dp) + Level 1 -> Level 2 elevation, via Expressive springs.
    val corner by animateDpAsState(
        targetValue = if (pressed) 20.dp else 16.dp,
        animationSpec = TaskFlowMotion.spatialFast(),
        label = "kanbanCardCorner",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 3.dp else 1.dp,
        animationSpec = TaskFlowMotion.spatialFast(),
        label = "kanbanCardElevation",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isOptimistic) OPTIMISTIC_ALPHA else 1f),
        shape = RoundedCornerShape(corner),
        color = scheme.surfaceContainerLowest,
        contentColor = scheme.onSurface,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = if (isSelected) BorderStroke(2.dp, scheme.primary) else null,
        interactionSource = interaction,
    ) {
        KanbanCardBody(card = card, isOptimistic = isOptimistic, assignee = assignee)
    }
}

/** The card's padded content column: title, labels, optional image thumbnail, and footer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KanbanCardBody(card: Card, isOptimistic: Boolean, assignee: AccountSummary?) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    Column(
        modifier = Modifier.padding(Dimens.space4),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (card.labels.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                card.labels.forEach { LabelChip(it) }
            }
        }

        val image = remember(card.attachments) {
            card.attachments.firstOrNull { it is Attachment.Image } as? Attachment.Image
        }
        if (image != null) {
            AsyncImage(
                model = image.url,
                contentDescription = image.alt,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(THUMBNAIL_HEIGHT)
                    .clip(shapes.medium),
            )
        }

        KanbanCardFooter(labelCount = card.labels.size, isOptimistic = isOptimistic, assignee = assignee)
    }
}

/** Footer row: Body Small meta (or a saving chip when optimistic) and the assignee avatar. */
@Composable
private fun KanbanCardFooter(labelCount: Int, isOptimistic: Boolean, assignee: AccountSummary?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isOptimistic) {
            SavingChip()
        } else {
            Text(
                text = "$labelCount labels",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (assignee != null) {
            Avatar(
                name = assignee.displayName,
                avatarUrl = assignee.avatarUrl,
                seedId = assignee.id.v,
                size = 24.dp,
            )
        }
    }
}

/** A small "Saving" status chip in the semantic `saving` (secondary) color, for optimistic cards. */
@Composable
private fun SavingChip() {
    val semantic = LocalSemanticColors.current
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(semantic.saving)
            .padding(horizontal = Dimens.space2, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Saving…",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

private const val OPTIMISTIC_ALPHA = 0.6f
private val THUMBNAIL_HEIGHT = 96.dp
