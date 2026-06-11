package org.reduxkotlin.sample.taskflow.feature.collaborators

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.reduxkotlin.Store
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.feature.board.BoardModel
import org.reduxkotlin.sample.taskflow.feature.board.BotMovedCard
import kotlin.random.Random

/**
 * Starts the simulated collaborator: a cancellable coroutine that periodically dispatches a
 * **non-`Undoable`** bot mutation, modelling an external actor editing the same board.
 *
 * The loop ticks every `settings().botIntervalMs`; each tick is skipped when the bot is disabled
 * (`!settings().botEnabled`) or no board is loaded. Otherwise it picks — deterministically given
 * [rngSeed] plus the current board — a card from a populated column and dispatches a
 * [BotMovedCard] moving it one column forward or backward (see [pickMove]; the backward moves keep
 * the walk non-absorbing so the board never drains into the terminal column). Because
 * [BotMovedCard] is not `Undoable` and is
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
 * Picks a deterministic move to an ADJACENT column: chooses a card from any populated column and
 * moves it one column forward or backward (direction uniform among the valid neighbours; clamped
 * at the first/last column). Returns `null` when no card is movable (the board is empty or has a
 * single column).
 *
 * Backward moves are what keep the walk non-absorbing. A forward-only bot turns the terminal
 * column into an absorbing state: within minutes every card piles up in "Done" and the earlier
 * columns render their empty state — which reads as data loss (it surfaced as a "no cards after
 * process-death restore" bug report).
 */
private fun pickMove(board: Board, rng: Random): BotMovedCard? {
    val lastIndex = board.columns.lastIndex
    val populated = board.columns.withIndex().filter { (_, column) -> column.cardIds.isNotEmpty() }
    if (lastIndex < 1 || populated.isEmpty()) return null

    val (columnIndex, column) = populated[rng.nextInt(populated.size)]
    val cardId = column.cardIds[rng.nextInt(column.cardIds.size)]
    val targetIndex = when (columnIndex) {
        0 -> 1
        lastIndex -> lastIndex - 1
        else -> if (rng.nextBoolean()) columnIndex + 1 else columnIndex - 1
    }
    // Insert at the head of the target column (a deterministic, valid index for any column size).
    return BotMovedCard(cardId = cardId, to = board.columns[targetIndex].id, toIndex = 0)
}
