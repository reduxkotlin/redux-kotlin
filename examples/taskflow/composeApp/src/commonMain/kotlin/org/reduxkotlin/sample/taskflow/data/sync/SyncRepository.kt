package org.reduxkotlin.sample.taskflow.data.sync

import kotlinx.collections.immutable.PersistentList
import org.reduxkotlin.sample.taskflow.action.AddCard
import org.reduxkotlin.sample.taskflow.action.CardMoveRequested
import org.reduxkotlin.sample.taskflow.action.DeleteCard
import org.reduxkotlin.sample.taskflow.action.EditCard
import org.reduxkotlin.sample.taskflow.action.InverseOp
import org.reduxkotlin.sample.taskflow.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.data.remote.RemoteApi
import org.reduxkotlin.sample.taskflow.data.remote.toSyncOp
import org.reduxkotlin.sample.taskflow.model.AccountId
import org.reduxkotlin.sample.taskflow.model.AccountSummary
import org.reduxkotlin.sample.taskflow.model.ActivityEntry
import org.reduxkotlin.sample.taskflow.model.AppSettingsModel
import org.reduxkotlin.sample.taskflow.model.Board
import org.reduxkotlin.sample.taskflow.model.BoardId
import org.reduxkotlin.sample.taskflow.model.BoardSummary
import org.reduxkotlin.sample.taskflow.model.Card
import org.reduxkotlin.sample.taskflow.model.CardId
import org.reduxkotlin.sample.taskflow.model.ColumnId
import org.reduxkotlin.sample.taskflow.model.NavModel
import org.reduxkotlin.sample.taskflow.model.OpId
import kotlin.time.Instant

private const val NEW_CARD_INDEX = 0

/**
 * The local-first data gateway the effects middleware talks to.
 *
 * Every mutation method is the same three-step dance: (1) write the durable [LocalStore]
 * (instant, works offline), (2) [LocalStore.enqueue] the matching serializable
 * [org.reduxkotlin.sample.taskflow.data.remote.SyncOp] carrying its per-op [InverseOp] (so a
 * later `Rejected` push reconstructs the revert from the queued op), and (3) [SyncEngine.kick]
 * to attempt a drain. Reads delegate straight to the [LocalStore].
 *
 * @property local the durable offline cache + outbound queue.
 * @property remote the network seam (held so reads/fetches can extend here later; mutations route
 *   through [engine]).
 * @property engine the offline-first drain orchestrator kicked after each mutation.
 */
public class SyncRepository(
    private val local: LocalStore,
    // Held for the real-backend seam (reads/fetches extend here later); mutations route through engine.
    @Suppress("UnusedPrivateProperty") private val remote: RemoteApi,
    private val engine: SyncEngine,
) {
    // ---- mutations (local-first: write LocalStore, enqueue op, kick engine) ----

    /**
     * Moves [cardId] within [boardId] from [from] to [to] at [toIndex], then queues the move.
     *
     * @param accountId the owning account (the queue is per-account).
     * @param boardId board the card lives on.
     * @param cardId card being moved.
     * @param from source column.
     * @param to destination column.
     * @param toIndex destination index within [to].
     * @param opId stable id for the queued op.
     * @param inverse the revert applied if the backend rejects the move.
     */
    public suspend fun moveCard(
        accountId: AccountId,
        boardId: BoardId,
        cardId: CardId,
        from: ColumnId,
        to: ColumnId,
        toIndex: Int,
        opId: OpId,
        inverse: InverseOp,
    ) {
        local.moveCard(boardId, cardId, to, toIndex)
        val op = CardMoveRequested(cardId, from, to, toIndex, opId).toSyncOp(inverse)
        local.enqueue(accountId, op)
        engine.kick(accountId)
    }

    /**
     * Adds [card] to [boardId] in [columnId], then queues the add.
     *
     * @param accountId the owning account.
     * @param boardId board to add to.
     * @param columnId destination column.
     * @param card the fully-materialized card to insert.
     * @param opId stable id for the queued op.
     * @param inverse the revert applied if the backend rejects the add.
     */
    public suspend fun addCard(
        accountId: AccountId,
        boardId: BoardId,
        columnId: ColumnId,
        card: Card,
        opId: OpId,
        inverse: InverseOp,
    ) {
        local.addCard(boardId, card, columnId, NEW_CARD_INDEX)
        val action = AddCard(
            columnId = columnId,
            cardId = card.id,
            title = card.title,
            description = card.description,
            opId = opId,
            now = card.createdAt,
        )
        local.enqueue(accountId, action.toSyncOp(card, inverse))
        engine.kick(accountId)
    }

    /**
     * Edits the title/description/timestamp of [cardId], then queues the edit.
     *
     * @param accountId the owning account.
     * @param cardId card being edited.
     * @param title new title.
     * @param description new description.
     * @param now the edit timestamp (minted at the dispatch site — never in a reducer).
     * @param opId stable id for the queued op.
     * @param inverse the revert applied if the backend rejects the edit.
     */
    public suspend fun editCard(
        accountId: AccountId,
        cardId: CardId,
        title: String,
        description: String,
        now: Instant,
        opId: OpId,
        inverse: InverseOp,
    ) {
        local.editCard(cardId, title, description, now)
        val op = EditCard(cardId, title, description, opId, now).toSyncOp(inverse)
        local.enqueue(accountId, op)
        engine.kick(accountId)
    }

    /**
     * Deletes [cardId], then queues the delete.
     *
     * @param accountId the owning account.
     * @param cardId card being deleted.
     * @param opId stable id for the queued op.
     * @param inverse the revert applied if the backend rejects the delete.
     */
    public suspend fun deleteCard(accountId: AccountId, cardId: CardId, opId: OpId, inverse: InverseOp) {
        local.deleteCard(cardId)
        val op = DeleteCard(cardId, opId).toSyncOp(inverse)
        local.enqueue(accountId, op)
        engine.kick(accountId)
    }

    /**
     * Kicks the engine to drain the queue and pull remote changes (manual refresh / reconnect).
     *
     * @param accountId the account to refresh.
     */
    public suspend fun refresh(accountId: AccountId) {
        engine.kick(accountId)
    }

    // ---- reads (delegate to the durable LocalStore) ----

    /** All persisted accounts as summaries. */
    public suspend fun loadAccounts(): PersistentList<AccountSummary> = local.loadAccounts()

    /** The boards belonging to [accountId], newest-updated first. */
    public suspend fun loadBoardList(accountId: AccountId): PersistentList<BoardSummary> =
        local.loadBoardList(accountId)

    /** Reassembles the full normalized [Board] for [boardId], or null if absent. */
    public suspend fun loadBoard(boardId: BoardId): Board? = local.loadBoard(boardId)

    /** The persisted nav state for [accountId] (defaults if never saved). */
    public suspend fun loadNav(accountId: AccountId): NavModel = local.loadNav(accountId)

    /** The newest-first activity feed for [accountId]. */
    public suspend fun loadActivity(accountId: AccountId): PersistentList<ActivityEntry> = local.loadActivity(accountId)

    /** The single-row app settings (defaults if never saved). */
    public suspend fun loadSettings(): AppSettingsModel = local.loadSettings()
}
