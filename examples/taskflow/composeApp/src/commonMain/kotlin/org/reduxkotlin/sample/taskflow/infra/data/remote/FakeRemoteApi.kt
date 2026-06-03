package org.reduxkotlin.sample.taskflow.infra.data.remote

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import org.reduxkotlin.sample.taskflow.core.Board
import org.reduxkotlin.sample.taskflow.core.BoardId
import org.reduxkotlin.sample.taskflow.core.Card
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.ColumnId
import org.reduxkotlin.sample.taskflow.core.FakeServiceConfig
import org.reduxkotlin.sample.taskflow.infra.SeedData
import kotlin.random.Random
import kotlin.time.Instant

/**
 * In-memory fake backend implementing [RemoteApi] for the sample.
 *
 * It owns a mutable server-side board snapshot seeded **identically** to the [LocalStore][org.reduxkotlin.sample.taskflow.infra.data.local.LocalStore]
 * (both consume [SeedData]), and reproduces real network behaviour from the live
 * [FakeServiceConfig] read via [config]: per-call **latency**, a **failure rate** (transient), and
 * an **offline** gate. Deterministic server validation rejects a card move into a column already at
 * its WIP limit — the conflict the sample's Rejected/inverse-revert flow demonstrates.
 *
 * The bot collaborator's "server-side" edits are injected through [botEdit] and surface on the next
 * [pull]. All randomness flows through the injected [rng] so virtual-time tests are reproducible.
 *
 * @param seededAccounts the seed content; the server snapshot is built from these boards.
 * @param config supplies the current [FakeServiceConfig] each call (so live Settings changes apply).
 * @param rng the deterministic source of latency jitter and failure rolls.
 */
public class FakeRemoteApi(
    seededAccounts: List<SeedData.SeededAccount>,
    private val config: () -> FakeServiceConfig,
    private val rng: Random,
) : RemoteApi {

    // The mutable server snapshot, keyed by board. Board/Column/Card are immutable; mutations
    // replace whole instances, preserving structural sharing of untouched rows.
    private val boards: MutableMap<BoardId, Board> =
        seededAccounts.associate { it.board.boardId to it.board }.toMutableMap()

    // Ordered server-side change log (bot edits). pull() returns entries strictly after `since`.
    private val changeLog: MutableList<Pair<Instant, RemoteChange>> = mutableListOf()

    /** A snapshot of the server-side [Board] for [boardId], or null. Exposed for tests / inspection. */
    public fun snapshot(boardId: BoardId): Board? = boards[boardId]

    /**
     * Injects a server-originated [change] (the bot's "server-side" edit) visible from [at].
     *
     * The change is appended to the pull log and applied to the server snapshot so subsequent
     * push validation (e.g. WIP counts) reflects it.
     *
     * @param change the remote change to surface.
     * @param at the timestamp the change becomes visible (callers pass a value after the last cursor).
     */
    public fun botEdit(change: RemoteChange, at: Instant) {
        changeLog += at to change
        applyRemoteChangeToSnapshot(change)
    }

    override suspend fun push(ops: List<SyncOp>): PushResult {
        applyLatency()
        val cfg = config()
        if (!cfg.online) throw OfflineException()
        if (rng.nextFloat() < cfg.failureRate) throw TransientNetworkException()

        // Validate the whole batch against a working copy first; reject without partial application.
        var working = boards
        for (op in ops) {
            val rejection = validate(op, working)
            if (rejection != null) return PushResult.Rejected(op.opId, rejection)
            working = applyOp(op, working)
        }
        // All validated — commit.
        boards.clear()
        boards.putAll(working)
        return PushResult.Accepted
    }

    override suspend fun pull(since: Instant?): RemotePage {
        applyLatency()
        if (!config().online) throw OfflineException()

        val pending = changeLog.filter { (at, _) -> since == null || at > since }
        if (pending.isEmpty()) {
            // Rule F: no work, no allocation churn — echo the caller's cursor.
            return RemotePage(persistentListOf(), since)
        }
        val cursor = pending.maxOf { it.first }
        return RemotePage(pending.map { it.second }.toPersistentList(), cursor)
    }

    // ---- latency / validation / application ----

    private suspend fun applyLatency() {
        val cfg = config()
        val min = cfg.latencyMinMs.toLong()
        val max = cfg.latencyMaxMs.toLong()
        val millis = if (max <= min) min else min + rng.nextLong(max - min + 1)
        if (millis > 0) delay(millis)
    }

    /** Returns a rejection reason for [op], or null if it is valid against [snapshot]. */
    private fun validate(op: SyncOp, snapshot: Map<BoardId, Board>): String? = when (op) {
        is SyncOp.Move -> {
            val board = boardOfCard(CardId(op.cardId), snapshot)
            val target = board?.columns?.firstOrNull { it.id.v == op.to }
            val limit = target?.wipLimit
            val alreadyThere = target?.cardIds?.any { it.v == op.cardId } == true
            if (limit != null && !alreadyThere && target.cardIds.size >= limit) "WIP limit reached" else null
        }

        is SyncOp.Add, is SyncOp.Edit, is SyncOp.Delete -> null
    }

    /** Applies [op] to a copy of [snapshot] and returns the new map (untouched boards are shared). */
    private fun applyOp(op: SyncOp, snapshot: Map<BoardId, Board>): MutableMap<BoardId, Board> {
        val next = snapshot.toMutableMap()
        val board = when (op) {
            is SyncOp.Add -> boardContaining(ColumnId(op.columnId), next)
            is SyncOp.Move, is SyncOp.Edit, is SyncOp.Delete -> boardOfCard(CardId(op.cardId), next)
        }
        if (board != null) {
            next[board.boardId] = applyOpToBoard(op, board)
        }
        return next
    }

    private fun applyOpToBoard(op: SyncOp, board: Board): Board = when (op) {
        is SyncOp.Move -> board.withCardMoved(CardId(op.cardId), ColumnId(op.to), op.toIndex)

        is SyncOp.Add -> board.withCardAdded(op.card.toDomain(), ColumnId(op.columnId))

        is SyncOp.Edit -> board.withCardEdited(
            CardId(op.cardId),
            op.title,
            op.description,
            Instant.fromEpochMilliseconds(op.nowMillis),
        )

        is SyncOp.Delete -> board.withCardRemoved(CardId(op.cardId))
    }

    private fun applyRemoteChangeToSnapshot(change: RemoteChange) {
        when (change) {
            is RemoteChange.CardUpserted -> {
                val board = boardContaining(change.columnId, boards)
                if (board != null) {
                    boards[board.boardId] = board
                        .withCardRemoved(change.card.id)
                        .withCardAdded(change.card, change.columnId, change.sortIndex)
                }
            }

            is RemoteChange.CardDeleted -> {
                val board = boardOfCard(change.cardId, boards)
                if (board != null) {
                    boards[board.boardId] = board.withCardRemoved(change.cardId)
                }
            }

            // Columns are fixed in the sample's seed; a summary-only change has no snapshot card impact.
            is RemoteChange.ColumnUpserted, is RemoteChange.BoardUpserted -> Unit
        }
    }

    private fun boardOfCard(cardId: CardId, snapshot: Map<BoardId, Board>): Board? =
        snapshot.values.firstOrNull { it.cards.containsKey(cardId) }

    private fun boardContaining(columnId: ColumnId, snapshot: Map<BoardId, Board>): Board? =
        snapshot.values.firstOrNull { board -> board.columns.any { it.id == columnId } }
}

// ---- immutable board mutators (server-side; integrity-preserving) ----

private fun Board.withCardMoved(cardId: CardId, to: ColumnId, toIndex: Int): Board {
    val cleared = columns.map { col -> col.copy(cardIds = col.cardIds.removeCardId(cardId)) }
    val updated = cleared.map { col ->
        if (col.id == to) {
            val idx = toIndex.coerceIn(0, col.cardIds.size)
            col.copy(cardIds = col.cardIds.add(idx, cardId))
        } else {
            col
        }
    }
    return copy(columns = updated.toPersistentList())
}

private fun Board.withCardAdded(card: Card, columnId: ColumnId, index: Int = 0): Board {
    val updatedColumns = columns.map { col ->
        if (col.id == columnId) {
            val idx = index.coerceIn(0, col.cardIds.size)
            col.copy(cardIds = col.cardIds.add(idx, card.id))
        } else {
            col
        }
    }
    return copy(columns = updatedColumns.toPersistentList(), cards = cards.put(card.id, card))
}

private fun Board.withCardEdited(cardId: CardId, title: String, description: String, updatedAt: Instant): Board {
    val existing = cards[cardId] ?: return this
    val edited = existing.copy(title = title, description = description, updatedAt = updatedAt)
    return copy(cards = cards.put(cardId, edited))
}

private fun Board.withCardRemoved(cardId: CardId): Board {
    val updatedColumns = columns.map { col -> col.copy(cardIds = col.cardIds.removeCardId(cardId)) }
    return copy(columns = updatedColumns.toPersistentList(), cards = cards.remove(cardId))
}

private fun PersistentList<CardId>.removeCardId(cardId: CardId): PersistentList<CardId> =
    if (contains(cardId)) remove(cardId) else this
