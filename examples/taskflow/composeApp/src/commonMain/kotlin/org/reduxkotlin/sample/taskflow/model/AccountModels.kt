package org.reduxkotlin.sample.taskflow.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.time.Instant

// --- Per-account models (ALL declared up front; board slices start empty/sentinel) ---
// Identity (name/email/avatar) is NOT duplicated here — it lives once in CollaboratorsModel
// (which includes self). SessionModel holds only the id + session-only fields.

data class SessionModel(val accountId: AccountId, val bio: String? = null)

// Seed/DTO used to construct an account store. NOT a store model — carries the
// account id + the seed identity/collaborators needed to build the per-account store.
data class AccountDetail(
    val accountId: AccountId,
    val self: AccountSummary,
    val collaborators: PersistentMap<AccountId, AccountSummary> = persistentMapOf(),
)

data class NavModel(
    val route: Route = Route.BoardList,
    val openCardId: CardId? = null,
    val composing: ColumnId? = null, // non-null => card-detail in CREATE mode
)

sealed interface Route {
    data object BoardList : Route
    data class Board(val boardId: BoardId) : Route
    data object Profile : Route
    data object Settings : Route
}

data class BoardListModel(
    val boards: PersistentMap<BoardId, BoardSummary> = persistentMapOf(),
    val order: PersistentList<BoardId> = persistentListOf(),
)

data class BoardSummary(
    val id: BoardId,
    val name: String,
    val color: Long,
    val cardCount: Int,
    val doneCount: Int,
    val updatedAt: Instant,
)

// Resolves assignee/creator/bot avatars within the account store (no root reach-in).
data class CollaboratorsModel(val byId: PersistentMap<AccountId, AccountSummary> = persistentMapOf())
