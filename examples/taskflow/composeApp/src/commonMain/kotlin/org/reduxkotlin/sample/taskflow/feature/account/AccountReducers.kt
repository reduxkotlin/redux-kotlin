package org.reduxkotlin.sample.taskflow.feature.account

import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.Action

/**
 * Pure root-store reducer for the [AccountsModel] slice (logged-in account directory + active id).
 *
 * Returns the same [model] instance unchanged for actions it does not handle so that the routing
 * layer can treat an identity result as a no-op.
 *
 * @param model the current accounts slice.
 * @param action the dispatched action.
 * @return the next accounts slice, or [model] unchanged when [action] is not handled.
 */
public fun accountsReducer(model: AccountsModel, action: Action): AccountsModel = when (action) {
    is AccountLoggedIn -> model.copy(
        accounts = model.accounts.put(action.summary.id, action.summary),
        activeAccountId = action.summary.id,
    )

    is SwitchAccount -> model.copy(activeAccountId = action.accountId)

    is LogoutAccount -> {
        val remaining = model.accounts.remove(action.accountId)
        model.copy(
            accounts = remaining,
            activeAccountId = if (model.activeAccountId == action.accountId) {
                remaining.keys.firstOrNull()
            } else {
                model.activeAccountId
            },
        )
    }

    is LoadAccountsSucceeded -> model.copy(
        accounts = persistentMapOf<AccountId, AccountSummary>()
            .mutate { builder -> action.accounts.forEach { builder[it.id] = it } },
        activeAccountId = action.activeAccountId,
    )

    is EditProfile -> {
        val activeId = model.activeAccountId
        val current = activeId?.let { model.accounts[it] }
        if (activeId == null || current == null) {
            model
        } else {
            model.copy(
                accounts = model.accounts.put(
                    activeId,
                    current.copy(
                        displayName = action.displayName,
                        email = action.email,
                        avatarUrl = action.avatarUrl,
                    ),
                ),
            )
        }
    }

    else -> model
}

/**
 * Pure root-store reducer for the [AuthFlowModel] slice (login/add-account flow state).
 *
 * Returns the same [model] instance unchanged for actions it does not handle.
 *
 * @param model the current auth-flow slice.
 * @param action the dispatched action.
 * @return the next auth-flow slice, or [model] unchanged when [action] is not handled.
 */
public fun authFlowReducer(model: AuthFlowModel, action: Action): AuthFlowModel = when (action) {
    is StartLogin -> model.copy(mode = action.mode, error = null)
    is LoginRequested -> model.copy(inFlight = true, error = null)
    is AccountLoggedIn -> model.copy(inFlight = false, error = null)
    is LoginFailed -> model.copy(inFlight = false, error = action.error)
    else -> model
}

/**
 * Pure per-account reducer for the [SessionModel] slice (account id + session-only bio).
 *
 * Identity (name/email/avatar) lives in [org.reduxkotlin.sample.taskflow.feature.collaborators.CollaboratorsModel], not here; only the bio is updated.
 * Returns the same [model] instance unchanged for actions it does not handle.
 *
 * @param model the current session slice.
 * @param action the dispatched action.
 * @return the next session slice, or [model] unchanged when [action] is not handled.
 */
public fun sessionReducer(model: SessionModel, action: Action): SessionModel = when (action) {
    is EditProfile -> model.copy(bio = action.bio)
    else -> model
}
