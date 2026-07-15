package org.reduxkotlin.sample.taskflow.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.reduxkotlin.Store
import org.reduxkotlin.compose.SelectorStore
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberSelectorStore
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens
import org.reduxkotlin.sample.taskflow.ui.theme.ShapeExtraLargeIncreased

/**
 * The account switcher (Screen 2): the registry-isolation surface. Lists every signed-in account
 * from the root [AccountsModel], marks the [AccountsModel.activeAccountId] row active, and lets the
 * user switch, log out, or add an account. Switching dispatches [SwitchAccount] (sets
 * `activeAccountId`), which makes Compose rebind the active account's registry store; logging out
 * dispatches [LogoutAccount] (disposes only that store); "Add account" calls [onAddAccount].
 *
 * Binding discipline (Rule C): the whole [AccountsModel] is read once through a single
 * [fieldStateOf] over the stable [rootStore]; the active-first display order is memoized via
 * [remember] keyed on the model fields, so no list work runs in the composable body. Each
 * [AccountRow] receives finished data plus remembered callbacks — the store never reaches a child.
 *
 * The sheet uses the Expressive "Extra Large Increased" (32 dp) [ShapeExtraLargeIncreased] shape
 * per the hi-fi spec. An empty-ish account list still renders the drag handle, title, and the
 * "Add account" affordance.
 *
 * @param rootStore the root app store holding [AccountsModel].
 * @param statusLineFor resolves a per-account status line (e.g. "on Sprint 42" / "on Settings"),
 *   supplied by the host from each account store's `NavModel` route — proof of per-store Nav
 *   isolation.
 * @param onAddAccount invoked when the "Add account" row is tapped (routes to Login in AddAccount
 *   mode).
 * @param modifier the [Modifier] for the sheet root.
 */
@Composable
public fun SwitcherScreen(
    rootStore: Store<ModelState>,
    statusLineFor: (AccountId) -> String,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitcherScreen(rememberSelectorStore(rootStore), statusLineFor, onAddAccount, modifier)
}

@Composable
internal fun SwitcherScreen(
    rootStore: SelectorStore<ModelState>,
    statusLineFor: (AccountId) -> String,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountsModel by rootStore.fieldStateOf(AccountsModel::class) { it }
    val accounts = accountsModel.accounts
    val activeId = accountsModel.activeAccountId

    // Active account first, then the rest in map order. Memoized so no list work runs per recompose.
    val ordered: List<AccountSummary> = remember(accounts, activeId) {
        val all = accounts.values.toList()
        val active = activeId?.let { accounts[it] }
        if (active == null) all else listOf(active) + all.filterNot { it.id == activeId }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeExtraLargeIncreased,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Dimens.space1,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Dimens.space4, vertical = Dimens.space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            DragHandle()
            Text(
                text = "Switch account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (ordered.isEmpty()) {
                EmptyAccountsHint()
            } else {
                for (account in ordered) {
                    val id = account.id
                    AccountRow(
                        account = account,
                        statusLine = statusLineFor(id),
                        isActive = id == activeId,
                        onClick = remember(rootStore, id) { { rootStore.dispatch(SwitchAccount(id)) } },
                        onLogout = remember(rootStore, id) { { rootStore.dispatch(LogoutAccount(id)) } },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.space1))
            AddAccountRow(onAddAccount = remember(onAddAccount) { onAddAccount })
        }
    }
}

/** The 34×4 dp grab handle centered at the top of the modal sheet. */
@Composable
private fun DragHandle() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(width = 34.dp, height = 4.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {}
    }
}

/** The hint shown when no accounts are present yet (only the "Add account" row remains useful). */
@Composable
private fun EmptyAccountsHint() {
    Text(
        text = "No accounts yet",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.space4),
    )
}

/** The dashed-avatar "Add account" affordance; [onAddAccount] routes to Login in AddAccount mode. */
@Composable
private fun AddAccountRow(onAddAccount: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onAddAccount,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.space4),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = scheme.surface,
                border = BorderStroke(2.dp, scheme.outlineVariant),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Add account",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.primary,
            )
        }
    }
}
