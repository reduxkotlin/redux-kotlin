package org.reduxkotlin.sample.taskflow.feature.board

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.AddCard
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardMoveRequested
import org.reduxkotlin.sample.taskflow.core.CardOpFailed
import org.reduxkotlin.sample.taskflow.core.CardOpSucceeded
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.DeleteCard
import org.reduxkotlin.sample.taskflow.core.EditCard
import org.reduxkotlin.sample.taskflow.core.InverseOp

/**
 * Pure per-account reducer for the [BoardModel] slice (the loaded board graph, or `null` = NotLoaded).
 *
 * Operates on `model.board`. While the board is `null` only [LoadBoardSucceeded] and [BoardRestored]
 * can transition it; every other action returns [model] unchanged. Card mutations are
 * integrity-preserving via [moveCard] (remove the id from every column, then insert once at a clamped
 * index), so a stale `from` column cannot orphan or duplicate a card. Timestamps come from the
 * action's pre-minted `now`; a move never changes a card's `updatedAt` (it carries no clock).
 * [CardOpFailed] reverts via the action's per-op [InverseOp], not a whole-board snapshot.
 *
 * @param model the current board slice.
 * @param action the dispatched action.
 * @param selfId the id of the self account this per-account store belongs to ([AddCard] carries no actor).
 * @return the next board slice, or [model] unchanged when [action] is not handled.
 */
public fun boardReducer(model: BoardModel, action: Action, selfId: AccountId): BoardModel = when (action) {
    // Lifecycle actions are valid regardless of load state.
    is LoadBoardSucceeded -> BoardModel(action.board)

    is BoardRestored -> BoardModel(action.board)

    is BoardClosed -> BoardModel(null)

    // Mutations apply only to a loaded board (null = NotLoaded -> unchanged).
    else -> {
        val board = model.board
        val next = board?.let { mutateBoard(it, action, selfId) }
        if (next === board) model else BoardModel(next)
    }
}

/**
 * Applies a card mutation to a loaded [board], returning the same [board] instance for actions that
 * are not card mutations so [boardReducer] preserves identity (the routing layer treats it as a no-op).
 */
private fun mutateBoard(board: Board, action: Action, selfId: AccountId): Board = when (action) {
    is CardMoveRequested -> moveCard(board, action.cardId, action.to, action.toIndex)

    is BotMovedCard -> moveCard(board, action.cardId, action.to, action.toIndex)

    is AddCard -> insertCard(
        board,
        action.columnId,
        Card(
            id = action.cardId,
            title = action.title,
            description = action.description,
            attachments = persistentListOf(),
            labels = persistentListOf(),
            assigneeId = null,
            createdBy = selfId,
            createdAt = action.now,
            updatedAt = action.now,
        ),
    )

    is BotAddedCard -> insertCard(board, action.columnId, action.card)

    is AddColumn -> board.copy(columns = board.columns.add(Column(action.id, action.title, persistentListOf())))

    is EditCard -> board.cards[action.cardId]?.let { existing ->
        board.copy(
            cards = board.cards.put(
                action.cardId,
                existing.copy(title = action.title, description = action.description, updatedAt = action.now),
            ),
        )
    } ?: board

    is DeleteCard -> deleteCard(board, action.cardId)

    is CardOpFailed -> applyInverse(board, action.inverse)

    else -> board
}

/**
 * Removes [cardId] from every column's `cardIds`, then inserts it once into the [to] column at
 * [toIndex] (coerced into range). Integrity-preserving: a stale source column cannot leave an orphan
 * or a duplicate. Unchanged [Column] instances are reused for structural sharing.
 */
private fun moveCard(board: Board, cardId: CardId, to: ColumnId, toIndex: Int): Board {
    val columns = board.columns.map { column ->
        when {
            column.id == to -> {
                val withoutCard = column.cardIds.remove(cardId)
                val index = toIndex.coerceIn(0, withoutCard.size)
                column.copy(cardIds = withoutCard.add(index, cardId))
            }

            column.cardIds.contains(cardId) -> column.copy(cardIds = column.cardIds.remove(cardId))

            else -> column
        }
    }.toPersistentList()
    return board.copy(columns = columns)
}

/**
 * Puts [card] into `cards` and inserts its id into the [columnId] column at [index] (clamped), or
 * appends it when [index] is `null`. Shared by `AddCard`/`BotAddedCard` (append) and the
 * re-add-deleted inverse (insert at the original index).
 */
private fun insertCard(board: Board, columnId: ColumnId, card: Card, index: Int? = null): Board {
    val columns = board.columns.map { column ->
        if (column.id == columnId) {
            val ids = column.cardIds
            val at = index?.coerceIn(0, ids.size) ?: ids.size
            column.copy(cardIds = ids.add(at, card.id))
        } else {
            column
        }
    }.toPersistentList()
    return board.copy(columns = columns, cards = board.cards.put(card.id, card))
}

/** Removes [cardId] from `cards` and from whichever column holds it. */
private fun deleteCard(board: Board, cardId: CardId): Board {
    val columns = board.columns.map { column ->
        if (column.cardIds.contains(cardId)) column.copy(cardIds = column.cardIds.remove(cardId)) else column
    }.toPersistentList()
    return board.copy(columns = columns, cards = board.cards.remove(cardId))
}

/** Applies a per-op [InverseOp] to undo a rejected optimistic mutation. */
private fun applyInverse(board: Board, inverse: InverseOp): Board = when (inverse) {
    is InverseOp.MoveBack -> moveCard(board, inverse.cardId, inverse.to, inverse.index)
    is InverseOp.DeleteAdded -> deleteCard(board, inverse.cardId)
    is InverseOp.RestoreEdited -> board.copy(cards = board.cards.put(inverse.prev.id, inverse.prev))
    is InverseOp.ReAddDeleted -> insertCard(board, inverse.column, inverse.card, inverse.index)
}

/**
 * Pure per-account reducer for the [FilterModel] slice (board search/assignee/label filters).
 *
 * [ToggleFilterLabel] flips a label's membership in the active set; [BoardClosed] resets the filter.
 * Returns the same [model] instance unchanged for actions it does not handle.
 *
 * @param model the current filter slice.
 * @param action the dispatched action.
 * @return the next filter slice, or [model] unchanged when [action] is not handled.
 */
public fun filterReducer(model: FilterModel, action: Action): FilterModel = when (action) {
    is SetFilterQuery -> model.copy(query = action.query)

    is SetFilterAssignee -> model.copy(assignee = action.accountId)

    is ToggleFilterLabel ->
        if (model.labelIds.contains(action.labelId)) {
            model.copy(labelIds = model.labelIds.remove(action.labelId))
        } else {
            model.copy(labelIds = model.labelIds.add(action.labelId))
        }

    is BoardClosed -> FilterModel()

    else -> model
}

/**
 * Pure per-account reducer for the [SyncModel] slice (in-flight set + sync status projection).
 *
 * Keys `inFlight` by [CardId]: a card enters on every optimistic mutation request and leaves on its
 * op result (both [CardOpSucceeded] and [CardOpFailed] carry the `cardId`). Invariant: these mutation
 * actions are only ever dispatched while a board is open, so the effects layer always enqueues a
 * matching op and a result is guaranteed (the add is paired with a later removal); a card cannot strand
 * here. [BoardClosed] resets the slice as a backstop. If a no-board card-op path is ever added, drive
 * the `inFlight` add from the effect (when the op is actually enqueued) instead of from the raw action.
 * [SyncStatusChanged] folds only `online`/`pendingCount`/`lastSyncedAt`/`lastError`; `inFlight` stays
 * reducer-maintained and is never overwritten from the action. Returns the same [model] instance
 * unchanged for actions it does not handle.
 *
 * @param model the current sync slice.
 * @param action the dispatched action.
 * @return the next sync slice, or [model] unchanged when [action] is not handled.
 */
public fun syncReducer(model: SyncModel, action: Action): SyncModel = when (action) {
    is CardMoveRequested -> model.copy(inFlight = model.inFlight.add(action.cardId))

    is AddCard -> model.copy(inFlight = model.inFlight.add(action.cardId))

    is EditCard -> model.copy(inFlight = model.inFlight.add(action.cardId))

    is DeleteCard -> model.copy(inFlight = model.inFlight.add(action.cardId))

    is CardOpSucceeded -> model.copy(inFlight = model.inFlight.remove(action.cardId))

    is CardOpFailed -> model.copy(inFlight = model.inFlight.remove(action.cardId))

    is SyncStatusChanged -> model.copy(
        online = action.online,
        pendingCount = action.pendingCount,
        lastSyncedAt = action.lastSyncedAt,
        lastError = action.lastError,
    )

    is BoardClosed -> SyncModel()

    else -> model
}

// activityReducer + ACTIVITY_CAP moved to …feature.activity.ActivityReducer
