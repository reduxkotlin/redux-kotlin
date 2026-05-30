package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The board's filter/command bar: a search [TextField] (Medium 12 dp shape), a Filter button
 * (badged when [filterActive]), and Undo / Redo icon buttons (disabled when their respective
 * [canUndo] / [canRedo] flag is `false`).
 *
 * The spec calls for an Expressive `ButtonGroup` whose selected segment expands via shape-morph;
 * that API is internal in this material3 build, so this is the documented fallback: a plain
 * [Row] of buttons (per spec-data.js — `ButtonGroup -> Row of buttons`).
 *
 * Pure presentational (Rule C): immutable primitives in; every callback is remembered by the
 * caller. The component reads no store and runs no logic — the screen owns the query / undo state.
 *
 * @param query the current search query text.
 * @param onQueryChange invoked with the new query on each edit.
 * @param filterActive `true` shows a badge on the Filter button (a filter is applied).
 * @param onFilterClick invoked when the Filter button is tapped (e.g. to open the filter menu).
 * @param canUndo `true` enables the Undo button.
 * @param canRedo `true` enables the Redo button.
 * @param onUndo invoked when Undo is tapped.
 * @param onRedo invoked when Redo is tapped.
 * @param modifier the [Modifier] for this bar.
 */
@Composable
public fun FilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filterActive: Boolean,
    onFilterClick: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .semantics { contentDescription = "Search cards" },
            singleLine = true,
            placeholder = { Text("Search", style = MaterialTheme.typography.bodyMedium) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainer,
                unfocusedContainerColor = scheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        FilterButton(active = filterActive, onClick = onFilterClick)

        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.semantics { contentDescription = "Undo" },
        ) {
            // Text glyph stands in for an icon to avoid a material-icons-extended dependency.
            Text("↶", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.semantics { contentDescription = "Redo" },
        ) {
            Text("↷", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The Filter button: a tonal icon button carrying a small [Badge] overlay when [active]. */
@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    BadgedBox(
        modifier = Modifier.wrapContentSize(),
        badge = {
            if (active) {
                Badge(modifier = Modifier.semantics { contentDescription = "Filter active" })
            }
        },
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = "Filter" },
        ) {
            // Text glyph stands in for an icon to avoid a material-icons-extended dependency.
            Text("▽", style = MaterialTheme.typography.labelLarge)
        }
    }
}
