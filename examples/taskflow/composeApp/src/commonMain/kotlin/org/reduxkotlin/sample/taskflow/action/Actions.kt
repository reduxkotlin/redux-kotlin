package org.reduxkotlin.sample.taskflow.action

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.ActivityEntry
import org.reduxkotlin.sample.taskflow.model.AuthMode
import org.reduxkotlin.sample.taskflow.model.Board
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.BoardSummary
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.LabelId
import org.reduxkotlin.sample.taskflow.model.OpId
import org.reduxkotlin.sample.taskflow.model.Route
import org.reduxkotlin.sample.taskflow.model.Theme
import org.reduxkotlin.sample.taskflow.model.UndoModel
import kotlin.time.Instant

// User card mutations only — drives the undo/redo stack.
sealed interface Undoable

// Every concrete action implements Action.
sealed interface Action

// --- card mutations (optimistic + undoable + persisted) — carry OpId, pre-minted ids/clock ---
data class CardMoveRequested(
    val cardId: CardId,
    val from: ColumnId,
    val to: ColumnId,
    val toIndex: Int,
    val opId: OpId,
) : Action,
    Undoable

data class AddCard(
    val columnId: ColumnId,
    val cardId: CardId,
    val title: String,
    val description: String,
    val opId: OpId,
    val now: Instant,
) : Action,
    Undoable

data class EditCard(val cardId: CardId, val title: String, val description: String, val opId: OpId, val now: Instant) :
    Action,
    Undoable

data class DeleteCard(val cardId: CardId, val opId: OpId) :
    Action,
    Undoable

// --- async op results — carry cardId so syncReducer can clear SyncModel.inFlight (a Set<CardId>) ---
data class CardOpSucceeded(val opId: OpId, val cardId: CardId) : Action

data class CardOpFailed(val opId: OpId, val cardId: CardId, val error: String, val inverse: InverseOp) : Action

// Per-op inverse — rides the queued SyncOp; reconstructed on Rejected (no middleware-side map).
sealed interface InverseOp {
    data class MoveBack(val cardId: CardId, val to: ColumnId, val index: Int) : InverseOp
    data class DeleteAdded(val cardId: CardId) : InverseOp
    data class RestoreEdited(val prev: Card) : InverseOp
    data class ReAddDeleted(val card: Card, val column: ColumnId, val index: Int) : InverseOp
}

// --- bot (server-truth; NOT undoable, no revert) ---
data class BotMovedCard(val cardId: CardId, val to: ColumnId, val toIndex: Int) : Action

data class BotAddedCard(val columnId: ColumnId, val card: Card) : Action

// --- board lifecycle ---
data object BoardClosed : Action

data class BoardRestored(val board: Board) : Action

data class StartCreateCard(val columnId: ColumnId) : Action

data object CancelCreateCard : Action

data class AddColumn(val id: ColumnId, val title: String) : Action

data class CreateBoard(val boardId: BoardId, val name: String, val now: Instant) : Action

// --- nav ---

/**
 * Push or reset the navigation stack to land on [route].
 *
 * Pushed onto the current top for non-top-level routes ([Route.CardDetail],
 * [Route.ComposeCard]); a [Route.TopLevel] **resets** the stack to that destination's
 * canonical base (BoardList → `[BoardList]`, Board(id) → `[BoardList, Board(id)]`,
 * Profile/Settings → `[<self>]`).
 */
data class Navigate(val route: Route) : Action

/**
 * Pop the top frame of the navigation stack — the system back button, in action form.
 *
 * Special-case for [Route.CardDetail.Mode]: if the top is a card-detail in [Mode.Edit]
 * the reducer transitions to [Mode.View] (a mode flip on the same frame, not a pop).
 * No-op if the stack is at its root (the host's system-back exits/backgrounds the app).
 */
data object Back : Action

/** Flip the current [Route.CardDetail] to [Route.CardDetail.Mode.Edit]; no-op otherwise. */
data object EnterEditMode : Action

/** Convenience for `Navigate(Route.CardDetail(cardId))` — preserved for selector clarity. */
data class OpenCard(val cardId: CardId) : Action

/** Convenience for [Back] when the top of the stack is a [Route.CardDetail]. */
data object CloseCard : Action

// --- undo / filter ---
data object Undo : Action

data object Redo : Action

// Internal undo plumbing — dispatched by undoMiddleware; folded by the UndoModel slot reducer.
// snapshot the present board before an undoable mutation
data class PushUndo(val snapshot: Board) : Action

// commit the next undo stacks after an Undo/Redo step
data class SetUndoModel(val model: UndoModel) : Action

data class SetFilterQuery(val query: String) : Action

data class SetFilterAssignee(val accountId: AccountId?) : Action

data class ToggleFilterLabel(val labelId: LabelId) : Action

// --- board/account loads (per-account) ---
data class LoadBoardRequested(val boardId: BoardId) : Action

data class LoadBoardSucceeded(val board: Board) : Action

data class LoadBoardFailed(val boardId: BoardId, val error: String) : Action

data object LoadBoardListRequested : Action

data class LoadBoardListSucceeded(val summaries: PersistentList<BoardSummary>) : Action

data class LoadBoardListFailed(val error: String) : Action

// --- sync (per-account) — folded into SyncModel by syncReducer ---
data class SyncStatusChanged(
    val online: Boolean,
    val pendingCount: Int,
    val inFlight: PersistentSet<CardId>,
    val lastSyncedAt: Instant?,
    val lastError: String?,
) : Action

data object Refresh : Action

// --- profile / activity (per-account) ---
data class EditProfile(val displayName: String, val email: String, val avatarUrl: String, val bio: String?) : Action

data class RecordActivity(val entry: ActivityEntry) : Action

// --- auth / accounts / settings (root) ---
data class StartLogin(val mode: AuthMode) : Action

data object LoginRequested : Action

// (Succeeded-equivalent; documented deviation)
data class AccountLoggedIn(val summary: AccountSummary) : Action

data class LoginFailed(val error: String) : Action

data object LoadAccountsRequested : Action

data class LoadAccountsSucceeded(val accounts: PersistentList<AccountSummary>, val activeAccountId: AccountId?) :
    Action

data class LoadAccountsFailed(val error: String) : Action

data class SwitchAccount(val accountId: AccountId) : Action

data class LogoutAccount(val accountId: AccountId) : Action

data class SetTheme(val theme: Theme) : Action

data class SetLatency(val minMs: Int, val maxMs: Int) : Action

data class SetFailureRate(val rate: Float) : Action

data class SetBotEnabled(val enabled: Boolean) : Action

// writes AppSettingsModel.fakeService.online (root)
data class SetOnline(val online: Boolean) : Action
