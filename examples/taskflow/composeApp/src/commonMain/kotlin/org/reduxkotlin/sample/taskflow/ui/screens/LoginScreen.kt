package org.reduxkotlin.sample.taskflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.reduxkotlin.Store
import org.reduxkotlin.compose.multimodel.fieldStateOf
import org.reduxkotlin.compose.rememberStableStore
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.AccountLoggedIn
import org.reduxkotlin.sample.taskflow.action.LoginRequested
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.data.SeedData
import org.reduxkotlin.sample.taskflow.model.AuthFlowModel
import org.reduxkotlin.sample.taskflow.store.getModel
import org.reduxkotlin.sample.taskflow.ui.components.AccountRow
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * The login / add-account screen: a fake auth flow that picks one of the seeded demo identities
 * (ann / raj / mia from [SeedData]) and signs in without a password.
 *
 * Binding discipline (Rule C): reactive auth state ([AuthFlowModel.mode]/[AuthFlowModel.inFlight]/
 * [AuthFlowModel.error]) is read through a single [fieldStateOf] over the stable [rootStore]; the
 * seeded identity list is static data from [SeedData]. The only screen-level effect is the auth
 * simulation — `Continue` dispatches [LoginRequested] (sets `inFlight`), waits a fake latency taken
 * from [AppSettingsModel], then dispatches [AccountLoggedIn] for the picked account. Only the
 * selected-account id lives in local `remember`; everything else flows through the store.
 *
 * @param rootStore the root app store holding [AuthFlowModel] and [AppSettingsModel].
 * @param modifier the [Modifier] for the screen root.
 */
@Composable
public fun LoginScreen(rootStore: Store<ModelState>, modifier: Modifier = Modifier) {
    val store = rememberStableStore(rootStore).value
    val auth by store.fieldStateOf(AuthFlowModel::class) { it }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var selectedId by remember { mutableStateOf(SeedData.accounts.first().id) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surface)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .padding(Dimens.space6),
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.surfaceContainerLowest,
            tonalElevation = Dimens.space1,
        ) {
            Column(
                modifier = Modifier.padding(Dimens.space6),
                verticalArrangement = Arrangement.spacedBy(Dimens.space4),
            ) {
                LoginHeader()
                AccountPicker(
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                )

                if (auth.error != null) {
                    Text(
                        text = auth.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                }

                ContinueButton(
                    inFlight = auth.inFlight,
                    onContinue = {
                        val picked = SeedData.accounts.first { it.id == selectedId }
                        store.dispatch(LoginRequested)
                        scope.launch {
                            val latency = store.getModel<AppSettingsModel>().fakeService.latencyMaxMs
                            delay(latency.toLong())
                            store.dispatch(AccountLoggedIn(picked))
                        }
                    },
                )

                LoginFooter()
            }
        }
    }
}

/** The wordmark, subtitle, and "pick a demo account" section label. */
@Composable
private fun LoginHeader() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "TaskFlow",
        style = MaterialTheme.typography.displaySmall,
        color = scheme.onPrimaryContainer,
    )
    Text(
        text = "Sign in to continue",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
    )
    Text(
        text = "PICK A DEMO ACCOUNT",
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
    )
}

/** The "no password — demo data only" footer note. */
@Composable
private fun LoginFooter() {
    Text(
        text = "No password — demo data only",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The selectable list of seeded demo identities; [onSelect] reports the tapped [AccountId]. */
@Composable
private fun AccountPicker(selectedId: AccountId, onSelect: (AccountId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        for (account in SeedData.accounts) {
            val id = account.id
            AccountRow(
                account = account,
                statusLine = account.email,
                isActive = id == selectedId,
                onClick = { onSelect(id) },
            )
        }
    }
}

/** The full-width Continue button; swaps its label for a loader while [inFlight] is true. */
@Composable
private fun ContinueButton(inFlight: Boolean, onContinue: () -> Unit) {
    Button(
        onClick = onContinue,
        enabled = !inFlight,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.space2),
    ) {
        if (inFlight) {
            CircularProgressIndicator(
                modifier = Modifier.padding(vertical = Dimens.space1),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text = "Continue", style = MaterialTheme.typography.labelLarge)
        }
    }
}
