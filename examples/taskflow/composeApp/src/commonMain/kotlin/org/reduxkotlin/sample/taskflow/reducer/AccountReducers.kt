package org.reduxkotlin.sample.taskflow.reducer

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.action.Back
import org.reduxkotlin.sample.taskflow.action.CancelCreateCard
import org.reduxkotlin.sample.taskflow.action.CloseCard
import org.reduxkotlin.sample.taskflow.action.CreateBoard
import org.reduxkotlin.sample.taskflow.action.EnterEditMode
import org.reduxkotlin.sample.taskflow.action.LoadBoardListSucceeded
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.OpenCard
import org.reduxkotlin.sample.taskflow.action.StartCreateCard
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.Route
import org.reduxkotlin.sample.taskflow.feature.account.EditProfile
import org.reduxkotlin.sample.taskflow.model.BoardListModel
import org.reduxkotlin.sample.taskflow.model.CollaboratorsModel

/** Fallback tile color for a newly created board (Material 3 primary). */
public const val DEFAULT_BOARD_COLOR: Long = 0xFF6750A4

/**
 * Pure per-account reducer for the [NavModel] slice (the navigation stack).
 *
 * Stack rules:
 *  - [Navigate] to a [Route.TopLevel] **resets** the stack to that destination's canonical base
 *    (`BoardList → [BoardList]`, `Board(id) → [BoardList, Board(id)]`,
 *    `Profile / Settings → [<self>]`). This is what gives the nav-rail its "tab" feel and what
 *    makes back from a board return to the board list.
 *  - [Navigate] to a modal route ([Route.CardDetail], [Route.ComposeCard]) **pushes** it on top
 *    of the current stack. Pushing a `CardDetail` with the same `cardId` as the top is idempotent
 *    (avoids a duplicate frame); a `Mode` carried by the action is ignored on push — entry mode
 *    is always [Route.CardDetail.Mode.View], and [EnterEditMode] flips it later.
 *  - [EnterEditMode] flips the top `CardDetail` to [Route.CardDetail.Mode.Edit] in place
 *    (no push). [Back] flips it back to `View` (no pop). That mode flip is the user's "edit"
 *    stack frame in the spec — they observe back as "return to view of the same card."
 *  - [Back] pops one frame if `stack.size > 1`. At the root it's a no-op so the host's system
 *    back exits/backgrounds the app. The Edit→View flip above takes precedence over the pop.
 *  - Domain aliases [OpenCard] / [CloseCard] / [StartCreateCard] / [CancelCreateCard] forward
 *    to [Navigate] / [Back] with their respective routes — kept so screens can dispatch domain
 *    actions instead of constructing routes by hand, and so existing tests keep their shape.
 *
 * Returns the same [model] instance unchanged when [action] is not handled (or when the
 * resulting stack is identical, e.g. an idempotent `Navigate`).
 *
 * @param model the current nav slice.
 * @param action the dispatched action.
 * @return the next nav slice, or [model] unchanged when [action] is not handled.
 */
public fun navReducer(model: NavModel, action: Action): NavModel = when (action) {
    is Navigate -> model.applyStack(stackForNavigate(model, action.route))
    is EnterEditMode -> model.flipCardDetailMode(Route.CardDetail.Mode.Edit, requireOther = Route.CardDetail.Mode.Edit)
    is Back -> backReducer(model)
    is OpenCard -> navReducer(model, Navigate(Route.CardDetail(action.cardId)))
    is CloseCard -> if (model.current is Route.CardDetail) navReducer(model, Back) else model
    is StartCreateCard -> navReducer(model, Navigate(Route.ComposeCard(action.columnId)))
    is CancelCreateCard -> if (model.current is Route.ComposeCard) navReducer(model, Back) else model
    else -> model
}

/** Computes the next stack for a [Navigate] action — see [navReducer]'s KDoc for the rules. */
private fun stackForNavigate(model: NavModel, target: Route): PersistentList<Route> = when (target) {
    is Route.BoardList -> persistentListOf(Route.BoardList)

    is Route.Board -> persistentListOf(Route.BoardList, target)

    is Route.Profile -> persistentListOf(Route.Profile)

    is Route.Settings -> persistentListOf(Route.Settings)

    is Route.CardDetail -> {
        val top = model.current
        if (top is Route.CardDetail && top.cardId == target.cardId) {
            model.stack
        } else {
            model.stack.add(Route.CardDetail(target.cardId))
        }
    }

    is Route.ComposeCard ->
        if (model.current == target) model.stack else model.stack.add(target)
}

/**
 * Edit→View special-case + standard pop. Edit-on-top transitions to view in place; otherwise pop one
 * frame when not at the root; otherwise no-op.
 */
private fun backReducer(model: NavModel): NavModel {
    val top = model.current
    return when {
        top is Route.CardDetail && top.mode == Route.CardDetail.Mode.Edit ->
            model.copy(stack = model.stack.set(model.stack.lastIndex, top.copy(mode = Route.CardDetail.Mode.View)))

        model.stack.size > 1 -> model.copy(stack = model.stack.removeAt(model.stack.lastIndex))

        else -> model
    }
}

/** Returns `this` unchanged if [next] equals the current stack; otherwise copies with the new stack. */
private fun NavModel.applyStack(next: PersistentList<Route>): NavModel = if (next == stack) this else copy(stack = next)

/**
 * Flips the top [Route.CardDetail] to [to] mode. No-op when the top isn't a CardDetail or is already
 * in mode [requireOther].
 */
private fun NavModel.flipCardDetailMode(to: Route.CardDetail.Mode, requireOther: Route.CardDetail.Mode): NavModel {
    val top = current
    return if (top is Route.CardDetail && top.mode != requireOther) {
        copy(stack = stack.set(stack.lastIndex, top.copy(mode = to)))
    } else {
        this
    }
}

// sessionReducer moved to …feature.account.AccountReducers

/**
 * Pure per-account reducer for the [BoardListModel] slice (board tile cache + display order).
 *
 * [LoadBoardListSucceeded] replaces the cache, keying tiles by id and taking the DB-aggregate
 * counts as-is. [CreateBoard] appends a fresh tile using the action's pre-minted timestamp (never
 * a reducer-side clock). Returns the same [model] instance unchanged for actions it does not handle.
 *
 * @param model the current board-list slice.
 * @param action the dispatched action.
 * @return the next board-list slice, or [model] unchanged when [action] is not handled.
 */
public fun boardListReducer(model: BoardListModel, action: Action): BoardListModel = when (action) {
    is LoadBoardListSucceeded -> {
        val boards = persistentMapOf<BoardId, BoardSummary>().builder().apply {
            action.summaries.forEach { put(it.id, it) }
        }.build()
        model.copy(
            boards = boards,
            order = action.summaries.map { it.id }.toPersistentList(),
        )
    }

    is CreateBoard -> model.copy(
        boards = model.boards.put(
            action.boardId,
            BoardSummary(
                id = action.boardId,
                name = action.name,
                color = DEFAULT_BOARD_COLOR,
                cardCount = 0,
                doneCount = 0,
                updatedAt = action.now,
            ),
        ),
        order = model.order.add(action.boardId),
    )

    else -> model
}

/**
 * Pure per-account reducer for the [CollaboratorsModel] slice (account directory including self).
 *
 * [EditProfile] carries no account id, so [selfId] (captured by the per-account store closure)
 * identifies the self collaborator to update or insert. Returns the same [model] instance unchanged
 * for actions it does not handle.
 *
 * @param model the current collaborators slice.
 * @param action the dispatched action.
 * @param selfId the id of the self account this per-account store belongs to.
 * @return the next collaborators slice, or [model] unchanged when [action] is not handled.
 */
public fun collaboratorsReducer(model: CollaboratorsModel, action: Action, selfId: AccountId): CollaboratorsModel =
    when (action) {
        is EditProfile -> model.copy(
            byId = model.byId.put(
                selfId,
                model.byId[selfId]?.copy(
                    displayName = action.displayName,
                    email = action.email,
                    avatarUrl = action.avatarUrl,
                ) ?: AccountSummary(selfId, action.displayName, action.email, action.avatarUrl),
            ),
        )

        else -> model
    }
