package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * A controlled Write/Preview markdown editor (spec-data.js -> `MarkdownEditor`). The text is
 * owned by the caller via [value] + [onValueChange] (the screen keeps it in a transient
 * `remember` and commits on Save — keystrokes never reach the store, Rule C). A
 * [SingleChoiceSegmentedButtonRow] toggles between:
 *
 * - **Write** — a Body Large [TextField] on `surfaceContainerHigh` with an Extra Small (4 dp)
 *   shape, plus a small toolbar that inserts markdown tokens (bold, list, link) via
 *   [onValueChange]; and
 * - **Preview** — a read-only [MarkdownView] of the current [value].
 *
 * The empty-title error visual is a screen concern; this editor stays generic.
 *
 * Pure presentational: only the [value] string and the remembered [onValueChange] callback
 * cross the boundary. The Write/Preview tab is transient view state and lives in a local
 * `remember`.
 *
 * @param value the current markdown text (owned by the caller).
 * @param onValueChange invoked with the new text on each edit or toolbar insert.
 * @param modifier the [Modifier] for this editor.
 */
@Composable
public fun MarkdownEditor(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var preview by remember { mutableStateOf(false) }

    // imePadding() keeps the active write field above the soft keyboard (iOS + Android).
    Column(modifier = modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        WritePreviewToggle(preview = preview, onPreviewChange = { preview = it })

        if (preview) {
            MarkdownView(markdown = value, modifier = Modifier.fillMaxWidth())
        } else {
            MarkdownToolbar(value = value, onValueChange = onValueChange)
            WriteField(value = value, onValueChange = onValueChange)
        }
    }
}

/** The Write/Preview segmented toggle. */
@Composable
private fun WritePreviewToggle(preview: Boolean, onPreviewChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = !preview,
            onClick = { onPreviewChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.semantics { contentDescription = "Write tab" },
        ) {
            Text("Write", style = MaterialTheme.typography.labelLarge)
        }
        SegmentedButton(
            selected = preview,
            onClick = { onPreviewChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.semantics { contentDescription = "Preview tab" },
        ) {
            Text("Preview", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The Body Large write field on `surfaceContainerHigh` with an Extra Small (4 dp) shape. */
@Composable
private fun WriteField(value: String, onValueChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WRITE_FIELD_MIN_HEIGHT)
            .semantics { contentDescription = "Markdown editor" },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(4.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/** A small toolbar of buttons that insert markdown tokens (bold, list, link) into [value]. */
@Composable
private fun MarkdownToolbar(value: String, onValueChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
        ToolbarButton(
            label = "B",
            description = "Insert bold",
            onClick = { onValueChange(appendToken(value, "**bold**")) },
        )
        ToolbarButton(
            label = "• List",
            description = "Insert list item",
            onClick = { onValueChange(appendToken(value, "\n- item")) },
        )
        ToolbarButton(
            label = "Link",
            description = "Insert link",
            onClick = { onValueChange(appendToken(value, "[text](https://)")) },
        )
    }
}

/** One toolbar button: a compact [TextButton] with an a11y label. */
@Composable
private fun ToolbarButton(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Dimens.space2, vertical = Dimens.space1),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Appends a markdown [token] to [value], separating with a space when the text isn't empty. */
private fun appendToken(value: String, token: String): String =
    if (value.isEmpty() || value.endsWith("\n")) value + token.removePrefix("\n") else "$value $token".trimEnd()

private val WRITE_FIELD_MIN_HEIGHT = 120.dp
