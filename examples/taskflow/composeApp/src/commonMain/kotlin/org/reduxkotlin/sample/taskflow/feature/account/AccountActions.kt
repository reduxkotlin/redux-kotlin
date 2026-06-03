package org.reduxkotlin.sample.taskflow.feature.account

import kotlinx.collections.immutable.PersistentList
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.Action

// --- profile / activity (per-account) ---

/** Edit the current account's display name, email, avatar and bio. Cross-cutting: handled in accountsReducer, sessionReducer, and collaboratorsReducer. */
public data class EditProfile(val displayName: String, val email: String, val avatarUrl: String, val bio: String?) :
    Action

// --- auth / accounts (root) ---

/** Start the login or add-account auth flow. */
public data class StartLogin(val mode: AuthMode) : Action

/** Mark the auth request as in-flight (loading state). */
public data object LoginRequested : Action

/** (Succeeded-equivalent; documented deviation) Signal that the account has logged in. */
public data class AccountLoggedIn(val summary: AccountSummary) : Action

/** Signal that the login attempt failed with an error message. */
public data class LoginFailed(val error: String) : Action

/** Request loading the account directory. */
public data object LoadAccountsRequested : Action

/** Provide the loaded account directory and the active account id. */
public data class LoadAccountsSucceeded(val accounts: PersistentList<AccountSummary>, val activeAccountId: AccountId?) :
    Action

/** Signal that the account directory load failed. */
public data class LoadAccountsFailed(val error: String) : Action

/** Switch the active account to [accountId]. */
public data class SwitchAccount(val accountId: AccountId) : Action

/** Remove [accountId] from the logged-in account directory. */
public data class LogoutAccount(val accountId: AccountId) : Action
