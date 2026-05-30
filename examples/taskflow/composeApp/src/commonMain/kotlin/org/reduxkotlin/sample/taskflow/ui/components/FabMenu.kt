package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowMotion

/**
 * A floating action button that toggles a small column of labelled actions (Add card / Add column),
 * dimming the board behind a scrim while [expanded].
 *
 * The spec calls for an Expressive `FloatingActionButtonMenu` that morphs the FAB into the menu;
 * that API is internal in this material3 build, so this is the documented fallback: a
 * [FloatingActionButton] (`primaryContainer`) that toggles a labelled action column over a scrim
 * (per spec-data.js — `FAB-menu -> FAB + menu/sheet`). The expand/collapse uses [TaskFlowMotion]
 * spatial springs for the Expressive feel.
 *
 * Pure presentational (Rule C): `expanded` is hoisted; every callback is remembered by the caller.
 *
 * @param expanded `true` shows the action menu + scrim; `false` shows only the trigger FAB.
 * @param onToggle invoked to flip the menu open/closed (FAB tap and scrim tap).
 * @param onAddCard invoked when the "Add card" action is tapped.
 * @param onAddColumn invoked when the "Add column" action is tapped.
 * @param modifier the [Modifier] for this menu's root box (typically fills the content area so the
 *  scrim can cover it and the FAB anchors to the bottom-end).
 */
@Composable
public fun FabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddCard: () -> Unit,
    onAddColumn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        // Scrim dims the board while the menu is open; tapping it closes the menu.
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(TaskFlowMotion.effectsDefault()),
            exit = fadeOut(TaskFlowMotion.effectsDefault()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.scrim.copy(alpha = SCRIM_ALPHA))
                    .clickable(onClick = onToggle)
                    .semantics { contentDescription = "Close menu" },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimens.space4),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(TaskFlowMotion.spatialFast()) + scaleIn(
                    animationSpec = TaskFlowMotion.spatialFast(),
                    transformOrigin = TransformOrigin(1f, 1f),
                ),
                exit = fadeOut(TaskFlowMotion.spatialFast()) + scaleOut(
                    animationSpec = TaskFlowMotion.spatialFast(),
                    transformOrigin = TransformOrigin(1f, 1f),
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    FabAction(label = "Add card", onClick = onAddCard)
                    FabAction(label = "Add column", onClick = onAddColumn)
                }
            }

            FloatingActionButton(
                onClick = onToggle,
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) "Close create menu" else "Open create menu"
                },
            ) {
                // Text glyph stands in for an icon to avoid a material-icons-extended dependency.
                Text(if (expanded) "×" else "+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/** One labelled action row in the expanded menu, rendered as a small extended FAB. */
@Composable
private fun FabAction(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private const val SCRIM_ALPHA = 0.32f
