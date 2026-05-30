package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * A snackbar-style toast that surfaces a sync [message] (a failed optimistic op, or an undo/redo
 * confirmation). Renders on an `inverseSurface` / `inverseOnSurface` surface at Level 3, with an
 * optional Retry action (shown only when [onRetry] is non-null) and a dismiss action. Mirrors the
 * `SyncToast / Snackbar` spec entry (spec-data.js).
 *
 * Pure presentational (Rule C): immutable primitives in; all callbacks are remembered by the
 * caller. Retry re-dispatches the original op at the call site; this component just surfaces it.
 *
 * @param message the toast text (Body Medium).
 * @param isError `true` surfaces a failed op — tints the dismiss/message toward the inverse error.
 * @param onRetry invoked when Retry is tapped, or `null` to hide the Retry action (info/undo toast).
 * @param onDismiss invoked when the toast is dismissed.
 * @param modifier the [Modifier] for this toast.
 */
@Composable
public fun SyncToast(
    message: String,
    isError: Boolean,
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.space4, vertical = Dimens.space2),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) scheme.inversePrimary else scheme.inverseOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.inversePrimary),
                    modifier = Modifier.semantics { contentDescription = "Retry" },
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.inverseOnSurface),
                modifier = Modifier.semantics { contentDescription = "Dismiss" },
            ) {
                Text("Dismiss", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
