package org.reduxkotlin.sample.taskflow.feature.boardlist

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary

/** Fallback tile color for a newly created board (Material 3 primary). */
public const val DEFAULT_BOARD_COLOR: Long = 0xFF6750A4

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
