package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary

// --- Root AppStore models ---

data class AccountsModel(
    val accounts: PersistentMap<AccountId, AccountSummary> = persistentMapOf(),
    val activeAccountId: AccountId? = null,
)

data class AuthFlowModel(val mode: AuthMode = AuthMode.Login, val inFlight: Boolean = false, val error: String? = null)

enum class AuthMode { Login, AddAccount }
