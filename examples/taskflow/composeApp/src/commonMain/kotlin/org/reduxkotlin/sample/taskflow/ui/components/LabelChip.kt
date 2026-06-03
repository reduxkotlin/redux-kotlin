package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.reduxkotlin.sample.taskflow.core.Label
import org.reduxkotlin.sample.taskflow.ui.theme.LocalSemanticColors
import org.reduxkotlin.sample.taskflow.ui.theme.labelColor

/**
 * A small (8 dp corner) tonal chip for a card [Label]. The container/text colors resolve
 * from the seeded semantic palette ([LocalSemanticColors] keyed by [Label.name], e.g.
 * "backend", "p1"); unseeded labels fall back to the label's own packed `color: Long`, with
 * a luminance-derived on-color for contrast. Text is the label name in Label Small.
 *
 * Pure presentational: immutable [Label] in, no store, no callbacks.
 *
 * @param label the label to render.
 * @param modifier the [Modifier] for this chip.
 */
@Composable
public fun LabelChip(label: Label, modifier: Modifier = Modifier) {
    val seeded = LocalSemanticColors.current.labelColors(label.name)
    val container = seeded?.container ?: labelColor(label.color)
    val onColor = seeded?.on ?: onColorFor(container)

    Text(
        text = label.name,
        style = MaterialTheme.typography.labelSmall,
        color = onColor,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = label.name },
    )
}

/** Picks a readable on-color (dark or light) for an arbitrary [container] by its luminance. */
private fun onColorFor(container: Color): Color = if (container.luminance() > 0.5f) Color.Black else Color.White
