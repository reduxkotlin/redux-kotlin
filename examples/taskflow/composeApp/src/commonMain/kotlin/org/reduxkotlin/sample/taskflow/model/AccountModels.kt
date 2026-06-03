package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary

// --- Per-account models (ALL declared up front; board slices start empty/sentinel) ---
// Identity (name/email/avatar) is NOT duplicated here — it lives once in CollaboratorsModel
// (which includes self). SessionModel holds only the id + session-only fields.

data class SessionModel(val accountId: AccountId, val bio: String? = null)

data class BoardListModel(
    val boards: PersistentMap<BoardId, BoardSummary> = persistentMapOf(),
    val order: PersistentList<BoardId> = persistentListOf(),
)

// Resolves assignee/creator/bot avatars within the account store (no root reach-in).
data class CollaboratorsModel(val byId: PersistentMap<AccountId, AccountSummary> = persistentMapOf())
