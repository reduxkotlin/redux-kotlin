package org.reduxkotlin.sample.taskflow.ui.screens

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.model.BoardModel
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

/**
 * A value-equal snapshot of one card's column position, used by the card-detail [MoveToGroup] (Rule
 * C — all column-walking lives here, never in the composable body). Holds the card's current column,
 * the adjacent columns (or `null` at an edge), and a human overline (`COLUMN · BOARD`-style label).
 * Returned from a `selectorState` so the move controls only recompose when the card's column position
 * (or the overline) actually changes.
 *
 * @property from the card's current [ColumnId], or `null` when the card / board is absent.
 * @property prev the previous column's [ColumnId], or `null` at the left edge.
 * @property next the next column's [ColumnId], or `null` at the right edge.
 * @property overline the Label-Small overline shown in the card-detail header (the column title).
 */
public data class CardColumnNav(val from: ColumnId?, val prev: ColumnId?, val next: ColumnId?, val overline: String)

/**
 * Pure derivation of a card's column position for the card-detail overlay (Rule C). Finds the column
 * whose `cardIds` contain [cardId], then reports the previous / next column ids (null at the edges)
 * and an overline built from the owning column's title. The result is value-equal, so the move
 * controls recompose only when the card's column position genuinely changes.
 *
 * @param boardModel the bound [BoardModel] holding the board (or the not-loaded sentinel).
 * @param cardId the card whose column position to derive.
 * @return a [CardColumnNav] describing the card's column and its neighbours.
 */
public fun cardColumnNav(boardModel: BoardModel, cardId: CardId): CardColumnNav {
    val columns = boardModel.board?.columns
    val index = columns?.indexOfFirst { cardId in it.cardIds } ?: -1
    return if (columns == null || index < 0) {
        CardColumnNav(null, null, null, "")
    } else {
        CardColumnNav(
            from = columns[index].id,
            prev = if (index > 0) columns[index - 1].id else null,
            next = if (index < columns.lastIndex) columns[index + 1].id else null,
            overline = columns[index].title.uppercase(),
        )
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
