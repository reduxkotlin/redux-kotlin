package org.reduxkotlin.sample.taskflow.feature.board

import kotlinx.collections.immutable.PersistentSet
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.Action
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.LabelId
import kotlin.time.Instant

// --- bot (server-truth; NOT undoable, no revert) ---
data class BotMovedCard(val cardId: CardId, val to: ColumnId, val toIndex: Int) : Action

data class BotAddedCard(val columnId: ColumnId, val card: Card) : Action

// --- board lifecycle ---
data object BoardClosed : Action

data class BoardRestored(val board: Board) : Action

data class StartCreateCard(val columnId: ColumnId) : Action

data object CancelCreateCard : Action

data class AddColumn(val id: ColumnId, val title: String) : Action

// CreateBoard moved to …feature.boardlist.BoardListActions

// --- filter ---
data class SetFilterQuery(val query: String) : Action

data class SetFilterAssignee(val accountId: AccountId?) : Action

data class ToggleFilterLabel(val labelId: LabelId) : Action

// --- board/account loads (per-account) ---
data class LoadBoardRequested(val boardId: BoardId) : Action

data class LoadBoardSucceeded(val board: Board) : Action

data class LoadBoardFailed(val boardId: BoardId, val error: String) : Action

// --- sync (per-account) — folded into SyncModel by syncReducer ---
data class SyncStatusChanged(
    val online: Boolean,
    val pendingCount: Int,
    val inFlight: PersistentSet<CardId>,
    val lastSyncedAt: Instant?,
    val lastError: String?,
) : Action

data object Refresh : Action
