package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * One logged-in account in the account switcher: an [Avatar], the account's display name
 * (Title Medium) and a [statusLine] (Body Small, e.g. last-known screen), plus an optional
 * logout button. Tapping the row fires [onClick] (the screen sets `activeAccountId`).
 *
 * The active row reads as a Large (16 dp) `primaryContainer` surface with a `primary` outline;
 * inactive rows are flat (Level 0). Mirrors the `AccountRow` spec entry (spec-data.js).
 *
 * Pure presentational (Rule C): immutable [account] + primitives in; both callbacks are
 * remembered by the caller. The component reads no store and runs no logic.
 *
 * @param account the account to render (drives the avatar and name).
 * @param statusLine the secondary line (e.g. "On Board · 2m ago"); shown in Body Small.
 * @param isActive `true` renders the active treatment (primaryContainer + primary outline).
 * @param onClick invoked when the row is tapped (e.g. to switch to this account).
 * @param modifier the [Modifier] for this row.
 * @param onLogout invoked when the logout button is tapped, or `null` to hide the button.
 */
@Composable
public fun AccountRow(
    account: AccountSummary,
    statusLine: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLogout: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isActive) scheme.primaryContainer else scheme.surface,
        contentColor = if (isActive) scheme.onPrimaryContainer else scheme.onSurface,
        border = if (isActive) BorderStroke(1.dp, scheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.space4),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                name = account.displayName,
                avatarUrl = account.avatarUrl,
                seedId = account.id.v,
                presenceOnline = if (isActive) true else null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onLogout != null) {
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.semantics { contentDescription = "Log out ${account.displayName}" },
                ) {
                    // Text glyph stands in for an icon to avoid a material-icons-extended dependency.
                    Text("⎋", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
