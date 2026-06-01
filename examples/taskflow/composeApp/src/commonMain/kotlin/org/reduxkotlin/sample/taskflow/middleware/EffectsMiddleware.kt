@file:Suppress("TooManyFunctions") // one private effect handler per action kind, by design

package org.reduxkotlin.sample.taskflow.middleware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.reduxkotlin.Middleware
import org.reduxkotlin.Store
import org.reduxkotlin.middleware
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.action.AddCard
import org.reduxkotlin.sample.taskflow.action.AddColumn
import org.reduxkotlin.sample.taskflow.action.CardMoveRequested
import org.reduxkotlin.sample.taskflow.action.CreateBoard
import org.reduxkotlin.sample.taskflow.action.DeleteCard
import org.reduxkotlin.sample.taskflow.action.EditCard
import org.reduxkotlin.sample.taskflow.action.InverseOp
import org.reduxkotlin.sample.taskflow.action.LoadBoardFailed
import org.reduxkotlin.sample.taskflow.action.LoadBoardListRequested
import org.reduxkotlin.sample.taskflow.action.LoadBoardListSucceeded
import org.reduxkotlin.sample.taskflow.action.LoadBoardRequested
import org.reduxkotlin.sample.taskflow.action.LoadBoardSucceeded
import org.reduxkotlin.sample.taskflow.action.Refresh
import org.reduxkotlin.sample.taskflow.action.SetOnline
import org.reduxkotlin.sample.taskflow.action.SyncStatusChanged
import org.reduxkotlin.sample.taskflow.data.sync.SyncRepository
import org.reduxkotlin.sample.taskflow.model.Board
import org.reduxkotlin.sample.taskflow.model.BoardModel
import org.reduxkotlin.sample.taskflow.model.NavModel
import org.reduxkotlin.sample.taskflow.model.columnById
import org.reduxkotlin.sample.taskflow.model.newBoardColumns
import org.reduxkotlin.sample.taskflow.reducer.DEFAULT_BOARD_COLOR

private const val DEFAULT_INDEX = 0

/**
 * The side-effect middleware: the SINGLE place that turns dispatched intent into [SyncRepository]
 * calls and folds the sync layer's results back into the store.
 *
 * It is local-first and optimistic. For each card mutation it (1) captures the present board and
 * computes the per-op [InverseOp] BEFORE the reducer runs, (2) lets the optimistic reducer update
 * the tree via `next(action)`, then (3) launches the matching [SyncRepository] mutate carrying that
 * inverse. If the backend later rejects, the repository's [SyncRepository.rejectEvents] emits a
 * `CardOpFailed` (with the inverse) that this middleware dispatches so the reducer reverts exactly
 * that op. Connectivity status flows back through [SyncRepository.status] as [SyncStatusChanged].
 *
 * Threading (Rule E): all repository work runs on [scope] (off-main); dispatches marshal to main
 * via the store's NotificationContext, so no explicit main hop is needed here.
 *
 * @param syncRepo the local-first data gateway (owns the engine + exposes reject/status flows).
 * @param scope the long-lived background scope effects launch on.
 * @return the assembled [Middleware] over [ModelState].
 */
public fun effectsMiddleware(syncRepo: SyncRepository, scope: CoroutineScope): Middleware<ModelState> {
    // Launch the reject/status collectors exactly once, on the first invocation (which has `store`).
    var collectorsStarted = false

    return middleware { store, next, action ->
        if (!collectorsStarted) {
            collectorsStarted = true
            startCollectors(syncRepo, scope, store)
        }
        handle(syncRepo, scope, store, next, action)
    }
}

/** Launches the long-lived accept/reject/status collectors that fold the sync layer back into the store. */
private fun startCollectors(syncRepo: SyncRepository, scope: CoroutineScope, store: Store<ModelState>) {
    scope.launch { syncRepo.acceptEvents.collect { store.dispatch(it) } }
    scope.launch { syncRepo.rejectEvents.collect { store.dispatch(it) } }
    scope.launch {
        syncRepo.status.collect { s ->
            store.dispatch(
                SyncStatusChanged(
                    online = s.online,
                    pendingCount = s.pendingCount,
                    inFlight = s.inFlight,
                    lastSyncedAt = s.lastSyncedAt,
                    lastError = s.lastError,
                ),
            )
        }
    }
}

/** Routes [action] to its effect (card mutation / load / refresh) and returns `next(action)`'s result. */
@Suppress("CyclomaticComplexMethod")
private fun handle(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: Any,
): Any = when (action) {
    is CardMoveRequested -> onMove(syncRepo, scope, store, next, action)
    is AddCard -> onAdd(syncRepo, scope, store, next, action)
    is EditCard -> onEdit(syncRepo, scope, store, next, action)
    is DeleteCard -> onDelete(syncRepo, scope, store, next, action)
    is LoadBoardRequested -> onLoadBoard(syncRepo, scope, store, next, action)
    is LoadBoardListRequested -> onLoadBoardList(syncRepo, scope, store, next, action)
    is CreateBoard -> onCreateBoard(syncRepo, scope, next, action)
    is AddColumn -> onAddColumn(syncRepo, scope, store, next, action)
    is Refresh -> next(action).also { scope.launch { syncRepo.refresh() } }
    is SetOnline -> next(action).also { if (action.online) scope.launch { syncRepo.refresh() } }
    else -> next(action)
}

private fun onMove(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: CardMoveRequested,
): Any = optimistic(store, next, action) { board ->
    val inverse = InverseOp.MoveBack(
        cardId = action.cardId,
        to = action.from,
        index = board.columnById(action.from)?.cardIds?.indexOf(action.cardId) ?: DEFAULT_INDEX,
    )
    scope.launch {
        syncRepo.moveCard(
            boardId = board.boardId,
            cardId = action.cardId,
            from = action.from,
            to = action.to,
            toIndex = action.toIndex,
            opId = action.opId,
            inverse = inverse,
        )
    }
}

private fun onAdd(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: AddCard,
): Any = optimistic(store, next, action) { board ->
    // The optimistic reducer has materialized the card; read it back to push the real payload.
    val card = store.state.get<BoardModel>().board?.cards?.get(action.cardId)
    if (card != null) {
        scope.launch {
            syncRepo.addCard(
                boardId = board.boardId,
                columnId = action.columnId,
                card = card,
                opId = action.opId,
                inverse = InverseOp.DeleteAdded(cardId = action.cardId),
            )
        }
    }
}

private fun onEdit(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: EditCard,
): Any {
    val prev = store.state.get<BoardModel>().board?.cards?.get(action.cardId)
    val result = next(action)
    if (prev != null) {
        scope.launch {
            syncRepo.editCard(
                cardId = action.cardId,
                title = action.title,
                description = action.description,
                now = action.now,
                opId = action.opId,
                inverse = InverseOp.RestoreEdited(prev = prev),
            )
        }
    }
    return result
}

private fun onDelete(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: DeleteCard,
): Any {
    val board = store.state.get<BoardModel>().board
    val card = board?.cards?.get(action.cardId)
    val column = board?.columns?.firstOrNull { it.cardIds.contains(action.cardId) }
    val result = next(action)
    if (card != null && column != null) {
        val inverse = InverseOp.ReAddDeleted(
            card = card,
            column = column.id,
            index = column.cardIds.indexOf(action.cardId),
        )
        scope.launch { syncRepo.deleteCard(cardId = action.cardId, opId = action.opId, inverse = inverse) }
    }
    return result
}

private fun onLoadBoard(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: LoadBoardRequested,
): Any {
    val result = next(action)
    scope.launch {
        val board = syncRepo.local.loadBoard(action.boardId)
        when {
            board == null -> store.dispatch(LoadBoardFailed(action.boardId, "not found"))

            // Drop a late load if the user already navigated away (Rule: drop on board-left). Use
            // activeBoardId so a card-detail / compose overlay above the board still counts as "on
            // this board" — those overlays don't leave the board.
            store.state.get<NavModel>().activeBoardId == action.boardId ->
                store.dispatch(LoadBoardSucceeded(board))
        }
    }
    return result
}

private fun onLoadBoardList(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: LoadBoardListRequested,
): Any {
    val result = next(action)
    scope.launch { store.dispatch(LoadBoardListSucceeded(syncRepo.loadBoardList(syncRepo.accountId))) }
    return result
}

/**
 * Persists a newly-created board (the reducer added only the list tile): writes the board row plus its
 * default To Do / Doing / Done columns so that navigating into it loads a real, non-empty board.
 */
private fun onCreateBoard(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    next: (Any) -> Any,
    action: CreateBoard,
): Any {
    val result = next(action)
    scope.launch {
        syncRepo.local.createBoard(
            accountId = syncRepo.accountId,
            boardId = action.boardId,
            name = action.name,
            color = DEFAULT_BOARD_COLOR,
            updatedAt = action.now,
            columns = newBoardColumns(action.boardId),
        )
    }
    return result
}

/**
 * Persists a column appended to the open board. The reducer has already added it to [BoardModel];
 * this reads it back (with its committed sort index) and writes it to the durable store.
 */
private fun onAddColumn(
    syncRepo: SyncRepository,
    scope: CoroutineScope,
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: AddColumn,
): Any {
    val result = next(action)
    val board = store.state.get<BoardModel>().board
    val sortIndex = board?.columns?.indexOfFirst { it.id == action.id } ?: -1
    val column = board?.columns?.getOrNull(sortIndex)
    if (board != null && column != null) {
        scope.launch { syncRepo.local.addColumn(board.boardId, column, sortIndex) }
    }
    return result
}

/**
 * Runs the optimistic-mutation dance: read the present board, run [next] (the optimistic reducer
 * update), then invoke [effect] with the PRE-update board to compute the inverse and launch sync.
 *
 * If there is no present board, the action still flows through [next] but no sync is scheduled
 * (defensive — a card mutation with no board loaded cannot compute a meaningful inverse).
 */
private inline fun optimistic(
    store: Store<ModelState>,
    next: (Any) -> Any,
    action: Any,
    effect: (board: Board) -> Unit,
): Any {
    val present = store.state.get<BoardModel>().board
    val result = next(action)
    if (present != null) effect(present)
    return result
}
