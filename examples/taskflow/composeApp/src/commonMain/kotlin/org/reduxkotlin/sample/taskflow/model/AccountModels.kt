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

/**
 * Stack-based navigation model. The top of [stack] is the current screen.
 *
 * The bottom is always a [Route.TopLevel] (the most-recent nav-rail destination); modal
 * routes ([Route.CardDetail], [Route.ComposeCard]) are pushed on top of it and pop one
 * frame at a time. See [Route] for the navigation rules.
 *
 * Derived conveniences ([current], [activeBoardId], [openCardId], [composing]) preserve
 * the read-shape used by selectors before the stack refactor, so screens don't need to
 * scan the stack themselves.
 */
data class NavModel(val stack: PersistentList<Route> = persistentListOf(Route.BoardList)) {
    /** Top of the stack — what the user is currently looking at. */
    val current: Route get() = stack.last()

    /** Most-recent [Route.Board] beneath (or at) the top — `null` if no board is on the stack. */
    val activeBoardId: BoardId?
        get() = (stack.lastOrNull { it is Route.Board } as? Route.Board)?.boardId

    /** Non-null iff the top of the stack is a [Route.CardDetail] (in any [Route.CardDetail.Mode]). */
    val openCardId: CardId? get() = (current as? Route.CardDetail)?.cardId

    /** Non-null iff the top of the stack is a [Route.ComposeCard]. */
    val composing: ColumnId? get() = (current as? Route.ComposeCard)?.columnId
}

/**
 * A navigation destination. The reducer pushes/pops [Route]s onto/off [NavModel.stack].
 *
 * - [TopLevel] routes are reachable from the nav rail; navigating to one **resets** the
 *   stack to that route's canonical base (BoardList → `[BoardList]`, Board(id) →
 *   `[BoardList, Board(id)]`, Profile/Settings → `[<self>]`).
 * - Non-[TopLevel] routes ([CardDetail], [ComposeCard]) are modal and **push** onto the
 *   current stack.
 */
sealed interface Route {
    /** Marker for routes that reset the stack when navigated to (the nav-rail destinations). */
    sealed interface TopLevel : Route

    /** Boards index. The implicit root of the Boards-tab stack. */
    data object BoardList : TopLevel

    /** Single board (Kanban columns). Sits on top of [BoardList] when reached via the rail. */
    data class Board(val boardId: BoardId) : TopLevel

    /** Profile screen — nav-rail destination. */
    data object Profile : TopLevel

    /** Settings screen — nav-rail destination. */
    data object Settings : TopLevel

    /**
     * Card-detail overlay for [cardId], in either view or edit [mode]. Edit ↔ view is a
     * mode transition on the same stack frame — back from `Edit` goes to `View`, not a pop.
     */
    data class CardDetail(val cardId: CardId, val mode: Mode = Mode.View) : Route {
        /** Card-detail display mode: read-only [View] or inline-edit [Edit]. */
        enum class Mode { View, Edit }
    }

    /** Create-card overlay targeting [columnId]. Pops on cancel / commit. */
    data class ComposeCard(val columnId: ColumnId) : Route
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
