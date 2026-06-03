package org.reduxkotlin.sample.taskflow.feature.collaborators

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.BotMovedCard
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.model.BoardModel
import kotlin.random.Random

/**
 * Starts the simulated collaborator: a cancellable coroutine that periodically dispatches a
 * **non-`Undoable`** bot mutation, modelling an external actor editing the same board.
 *
 * The loop ticks every `settings().botIntervalMs`; each tick is skipped when the bot is disabled
 * (`!settings().botEnabled`) or no board is loaded. Otherwise it picks — deterministically given
 * [rngSeed] plus the current board — a card sitting in a non-terminal column and dispatches a
 * [BotMovedCard] advancing it one column forward. Because [BotMovedCard] is not `Undoable` and is
 * folded by the integrity-preserving `boardReducer` (remove from all columns, insert once), an
 * interleaved bot/user move can never orphan or duplicate a card, and the move is treated as
 * server-truth (no optimistic revert, not in the user's undo history).
 *
 * Caller owns the lifecycle: cancel the returned [Job] (e.g. on `BoardClosed`/logout) to stop it.
 *
 * @param scope the long-lived background scope the loop runs on.
 * @param store the per-account store the bot reads the board from and dispatches into.
 * @param settings reads the live [org.reduxkotlin.sample.taskflow.core.FakeServiceConfig] each tick,
 * so toggling `botEnabled` / `botIntervalMs` at runtime takes effect on the next tick.
 * @param rngSeed seeds the per-bot RNG so a given seed + board yields a deterministic move sequence.
 * @return the [Job] driving the loop; cancel it to stop the bot.
 */
public fun startBot(
    scope: CoroutineScope,
    store: Store<ModelState>,
    settings: () -> FakeServiceConfig,
    rngSeed: Long,
): Job = scope.launch {
    val rng = Random(rngSeed)
    while (isActive) {
        delay(settings().botIntervalMs.toLong())
        nextBotMove(store, settings(), rng)?.let { store.dispatch(it) }
    }
}

/**
 * Resolves this tick's bot move, or `null` when the tick should be skipped: the bot is disabled,
 * no board is loaded, or no card is movable. Folding the three skip conditions here keeps the loop
 * body to a single decision (dispatch-or-skip).
 */
private fun nextBotMove(store: Store<ModelState>, config: FakeServiceConfig, rng: Random): BotMovedCard? {
    val board = store.state.get<BoardModel>().board
    return if (config.botEnabled && board != null) pickMove(board, rng) else null
}

/**
 * Picks a deterministic forward move: chooses a card from a non-terminal column (one that has a
 * column after it) and advances it to the next column. Returns `null` when no card is movable
 * (every populated column is already the last one, or the board is empty).
 */
private fun pickMove(board: Board, rng: Random): BotMovedCard? {
    val lastIndex = board.columns.lastIndex
    // Columns that hold at least one card AND have a column after them (so a forward move exists).
    val movableColumns = board.columns.withIndex()
        .filter { (index, column) -> index < lastIndex && column.cardIds.isNotEmpty() }
    if (movableColumns.isEmpty()) return null

    val (columnIndex, column) = movableColumns[rng.nextInt(movableColumns.size)]
    val cardId = column.cardIds[rng.nextInt(column.cardIds.size)]
    val target = board.columns[columnIndex + 1]
    // Insert at the head of the next column (a deterministic, valid index for any column size).
    return BotMovedCard(cardId = cardId, to = target.id, toIndex = 0)
}
