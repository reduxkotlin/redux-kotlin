package org.reduxkotlin.sample.taskflow.ui.screens

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.model.Board
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.FilterModel
import org.reduxkotlin.sample.taskflow.model.columnById

/**
 * A value-equal, stable descriptor of one [org.reduxkotlin.sample.taskflow.model.Column] header:
 * just its identity and the fields a `ColumnView` needs to render its frame. Bound once per board
 * (Rule C) so the column-list composable never selects the whole board; the heavyweight `cardIds`
 * and per-card data stay behind each column's own narrow [BoardModel] subscription.
 *
 * @property id the column's stable [ColumnId] — used as the `key(...)` for each column composable.
 * @property title the column title shown in the [org.reduxkotlin.sample.taskflow.ui.components.ColumnHeader].
 * @property wipLimit the column's optional WIP limit, or `null` when no limit is set.
 */
public data class ColDesc(val id: ColumnId, val title: String, val wipLimit: Int?)

/**
 * A value-equal WIP snapshot for one column: the live [count] and the optional [limit]. Returned
 * from a `selectorState` so the WIP badge only recomposes when the count (or limit) actually moves,
 * never because a sibling column changed (Rule C).
 *
 * @property count the current number of cards in the column.
 * @property limit the column's WIP limit, or `null` when unset.
 */
public data class WipState(val count: Int, val limit: Int?)

/**
 * Pure filter derivation for one column (Rule C — all list work lives here / in reducers, never in
 * a composable body). For each [CardId] in the column's `cardIds`, the matching [Card] is kept when
 * it satisfies the active [FilterModel]:
 * - a non-blank [FilterModel.query] must appear (case-insensitive) in the card title or description;
 * - a non-null [FilterModel.assignee] must equal the card's `assigneeId`;
 * - a non-empty [FilterModel.labelIds] must intersect the card's label ids.
 *
 * An empty filter keeps the column's order untouched. The result is a stable
 * [PersistentList] of card ids, so a selector returning it only triggers a recomposition when the
 * visible set genuinely changes.
 *
 * @param boardModel the bound [BoardModel] holding the board (or the not-loaded sentinel).
 * @param filter the active [FilterModel].
 * @param columnId the column to derive visible card ids for.
 * @return the visible card ids in column order, or an empty list when the board/column is absent.
 */
public fun deriveVisibleCardIds(
    boardModel: BoardModel,
    filter: FilterModel,
    columnId: ColumnId,
): PersistentList<CardId> {
    val board = boardModel.board
    val column = board?.columnById(columnId)
    return when {
        column == null -> persistentListOf()
        !filter.isActive() -> column.cardIds
        else -> board.filtered(column.cardIds, filter)
    }
}

/** Keeps only the [cardIds] whose resolved [Card] matches [filter], as a stable [PersistentList]. */
private fun Board.filtered(cardIds: PersistentList<CardId>, filter: FilterModel): PersistentList<CardId> =
    cardIds.filter { cardId -> cards[cardId]?.let { filter.matches(it) } == true }.toPersistentList()

/** `true` when any filter dimension is set (query / assignee / labels). */
private fun FilterModel.isActive(): Boolean = query.isNotBlank() || assignee != null || labelIds.isNotEmpty()

/** `true` when [card] satisfies every active dimension of this filter. */
private fun FilterModel.matches(card: Card): Boolean {
    val q = query.trim()
    val queryOk = q.isEmpty() ||
        card.title.contains(q, ignoreCase = true) ||
        card.description.contains(q, ignoreCase = true)
    val assigneeOk = assignee == null || card.assigneeId == assignee
    val labelOk = labelIds.isEmpty() || card.labels.any { it.id in labelIds }
    return queryOk && assigneeOk && labelOk
}
