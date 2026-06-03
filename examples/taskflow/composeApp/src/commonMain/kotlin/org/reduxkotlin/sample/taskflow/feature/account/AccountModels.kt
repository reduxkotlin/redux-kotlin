package org.reduxkotlin.sample.taskflow.feature.account

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary

/** The logged-in account directory + active account id, held in the root app store. */
public data class AccountsModel(
    val accounts: PersistentMap<AccountId, AccountSummary> = persistentMapOf(),
    val activeAccountId: AccountId? = null,
)

/** The transient login / add-account flow state, held in the root app store. */
public data class AuthFlowModel(
    val mode: AuthMode = AuthMode.Login,
    val inFlight: Boolean = false,
    val error: String? = null,
)

/** Whether the auth flow is for a first [Login] or for [AddAccount] to an existing session. */
public enum class AuthMode { Login, AddAccount }

/** Per-account session state: the owning account id + session-only bio. Held in each per-account store. */
public data class SessionModel(val accountId: AccountId, val bio: String? = null)
