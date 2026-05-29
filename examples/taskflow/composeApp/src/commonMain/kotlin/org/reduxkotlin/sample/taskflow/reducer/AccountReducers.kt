package org.reduxkotlin.sample.taskflow.reducer

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.action.Action
import org.reduxkotlin.sample.taskflow.action.CancelCreateCard
import org.reduxkotlin.sample.taskflow.action.CloseCard
import org.reduxkotlin.sample.taskflow.action.CreateBoard
import org.reduxkotlin.sample.taskflow.action.EditProfile
import org.reduxkotlin.sample.taskflow.action.LoadBoardListSucceeded
import org.reduxkotlin.sample.taskflow.action.Navigate
import org.reduxkotlin.sample.taskflow.action.OpenCard
import org.reduxkotlin.sample.taskflow.action.StartCreateCard
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.BoardListModel
import org.reduxkotlin.sample.taskflow.model.BoardSummary
import org.reduxkotlin.sample.taskflow.model.CollaboratorsModel
import org.reduxkotlin.sample.taskflow.model.NavModel
import org.reduxkotlin.sample.taskflow.model.SessionModel

/** Fallback tile color for a newly created board (Material 3 primary). */
public const val DEFAULT_BOARD_COLOR: Long = 0xFF6750A4

/**
 * Pure per-account reducer for the [NavModel] slice (current route + open/composing card overlays).
 *
 * Leaving any screen via [Navigate] exits card-detail and card-create; the other actions only toggle
 * the relevant overlay. Returns the same [model] instance unchanged for actions it does not handle.
 *
 * @param model the current nav slice.
 * @param action the dispatched action.
 * @return the next nav slice, or [model] unchanged when [action] is not handled.
 */
public fun navReducer(model: NavModel, action: Action): NavModel = when (action) {
    is Navigate -> model.copy(route = action.route, openCardId = null, composing = null)
    is OpenCard -> model.copy(openCardId = action.cardId)
    is CloseCard -> model.copy(openCardId = null)
    is StartCreateCard -> model.copy(composing = action.columnId)
    is CancelCreateCard -> model.copy(composing = null)
    else -> model
}

/**
 * Pure per-account reducer for the [SessionModel] slice (account id + session-only bio).
 *
 * Identity (name/email/avatar) lives in [CollaboratorsModel], not here; only the bio is updated.
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
