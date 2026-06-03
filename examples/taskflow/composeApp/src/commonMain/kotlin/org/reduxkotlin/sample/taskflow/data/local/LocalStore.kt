package org.reduxkotlin.sample.taskflow.data.local

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.AccountSummary
import org.reduxkotlin.sample.taskflow.core.ActivityEntry
import org.reduxkotlin.sample.taskflow.core.AppSettingsModel
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.BoardSummary
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.Column
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.NavModel
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.data.remote.RemoteChange
import org.reduxkotlin.sample.taskflow.data.remote.SyncOp
import kotlin.time.Instant

/**
 * The durable offline cache. All operations are `suspend` and return deeply-immutable models.
 *
 * This is local persistence only — instant, with **no** artificial latency (the fake network
 * latency lives in `RemoteApi`). Reads always succeed offline; card mutations also enqueue
 * outbound [SyncOp]s into the pending-op queue for the sync engine to drain.
 */
@Suppress("TooManyFunctions") // the durable-cache contract intentionally exposes one op per concern
public interface LocalStore {
    // ---- reads ----

    /** All persisted accounts as summaries. */
    public suspend fun loadAccounts(): PersistentList<AccountSummary>

    /** The single-row app settings (defaults if never saved). */
    public suspend fun loadSettings(): AppSettingsModel

    /** The persisted active account id, or null. */
    public suspend fun loadActiveAccountId(): AccountId?

    /** The boards belonging to [accountId], newest-updated first. */
    public suspend fun loadBoardList(accountId: AccountId): PersistentList<BoardSummary>

    /** Reassembles the full normalized [Board] for [boardId], or null if absent. */
    public suspend fun loadBoard(boardId: BoardId): Board?

    /** The persisted nav state for [accountId] (defaults if never saved). */
    public suspend fun loadNav(accountId: AccountId): NavModel

    /** Collaborators referenceable on [accountId]'s cards, by id (includes self + bot). */
    public suspend fun loadCollaborators(accountId: AccountId): PersistentMap<AccountId, AccountSummary>

    /** The newest-first activity feed for [accountId]. */
    public suspend fun loadActivity(accountId: AccountId): PersistentList<ActivityEntry>

    // ---- writes ----

    /** Persists [settings] (single row), preserving the stored active-account id. */
    public suspend fun saveSettings(settings: AppSettingsModel)

    /** Persists the active [accountId] (or clears it when null). */
    public suspend fun saveActiveAccountId(accountId: AccountId?)

    /** Persists [nav] for [accountId]. */
    public suspend fun saveNav(accountId: AccountId, nav: NavModel)

    /** Moves [cardId] within [boardId] to [toColumn] at [toIndex], re-sequencing the column. */
    public suspend fun moveCard(boardId: BoardId, cardId: CardId, toColumn: ColumnId, toIndex: Int)

    /** Adds [card] to [boardId] in [columnId] at [index], re-sequencing the column. */
    public suspend fun addCard(boardId: BoardId, card: Card, columnId: ColumnId, index: Int)

    /** Creates [boardId] for [accountId] with [name]/[color]/[updatedAt] and its initial [columns]. */
    public suspend fun createBoard(
        accountId: AccountId,
        boardId: BoardId,
        name: String,
        color: Long,
        updatedAt: Instant,
        columns: List<Column>,
    )

    /** Appends [column] to [boardId] at [sortIndex]. */
    public suspend fun addColumn(boardId: BoardId, column: Column, sortIndex: Int)

    /** Edits the [title]/[description]/[updatedAt] of [cardId]. */
    public suspend fun editCard(cardId: CardId, title: String, description: String, updatedAt: Instant)

    /** Deletes [cardId] and its attachments/labels. */
    public suspend fun deleteCard(cardId: CardId)

    /** Records an activity [entry] for [accountId]. */
    public suspend fun recordActivity(accountId: AccountId, entry: ActivityEntry)

    // ---- outbound sync queue ----

    /** Enqueues [op] as a pending outbound op for [accountId]. */
    public suspend fun enqueue(accountId: AccountId, op: SyncOp)

    /** The pending outbound ops for [accountId], oldest first. */
    public suspend fun pendingOps(accountId: AccountId): List<SyncOp>

    /** Marks [opId] synced (removes it from the queue). */
    public suspend fun markSynced(opId: OpId)

    /** Increments the retry attempt counter for [opId]. */
    public suspend fun incrementAttempts(opId: OpId)

    // ---- remote merge ----

    /** Merges remote [changes] into the local cache (last-write-wins). */
    public suspend fun applyRemote(changes: List<RemoteChange>)

    /** The last successful sync time for [accountId], or null. */
    public suspend fun lastSyncedAt(accountId: AccountId): Instant?

    /** Persists the last successful sync time for [accountId]. */
    public suspend fun setLastSyncedAt(accountId: AccountId, at: Instant?)

    // ---- seed ----

    /** Seeds the database from [org.reduxkotlin.sample.taskflow.data.SeedData] if empty (idempotent). */
    public suspend fun ensureSeeded()
}
