package org.reduxkotlin.sample.taskflow.data.sync

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.reduxkotlin.sample.taskflow.core.AccountId
import org.reduxkotlin.sample.taskflow.core.CardId
import org.reduxkotlin.sample.taskflow.core.CardOpFailed
import org.reduxkotlin.sample.taskflow.core.CardOpSucceeded
import org.reduxkotlin.sample.taskflow.core.OpId
import org.reduxkotlin.sample.taskflow.data.local.LocalStore
import org.reduxkotlin.sample.taskflow.data.remote.OfflineException
import org.reduxkotlin.sample.taskflow.data.remote.PushResult
import org.reduxkotlin.sample.taskflow.data.remote.RemoteApi
import org.reduxkotlin.sample.taskflow.data.remote.TransientNetworkException
import org.reduxkotlin.sample.taskflow.data.remote.toDomain
import kotlin.time.Instant

/**
 * The offline-first sync orchestrator. Drains the durable outbound op queue ([LocalStore.pendingOps])
 * to [RemoteApi.push], then pulls remote changes back into the [LocalStore].
 *
 * It is deliberately UI-agnostic: instead of touching a Redux store it reports outcomes through two
 * callbacks — [onReject] (a server validation conflict the store reverts via the op's per-op inverse)
 * and [onStatus] (the projected [SyncStatus] the effects layer folds into `SyncModel`). Connectivity /
 * transient failures are NOT rejections: an [OfflineException] stops the drain (the queue stays intact
 * and retries on reconnect) and a [TransientNetworkException] bumps the op's attempt counter and
 * retries later.
 *
 * @property local the durable offline cache + outbound queue.
 * @property remote the network seam (real or [org.reduxkotlin.sample.taskflow.data.remote.FakeRemoteApi]).
 * @property scope the background scope drains run on (its dispatch path is off-main; the effects layer
 *   hops to Main before dispatching — Rule E).
 * @property onAccept invoked once per accepted op with a [CardOpSucceeded] so the store can clear the
 *   card from `SyncModel.inFlight` (the per-card "Saving…" / optimistic state).
 * @property onReject invoked once per rejected op with the reconstructed [CardOpFailed] so the store can
 *   apply the per-op inverse.
 * @property onStatus invoked with the current [SyncStatus] only on a real delta (Rule F).
 */
public class SyncEngine(
    private val local: LocalStore,
    private val remote: RemoteApi,
    private val scope: CoroutineScope,
    private val onAccept: (CardOpSucceeded) -> Unit,
    private val onReject: (CardOpFailed) -> Unit,
    private val onStatus: (SyncStatus) -> Unit,
) {
    // Serializes drains so only one runs at a time; a kick during a drain queues behind it.
    private val drainLock = Mutex()

    // The last status emitted, so onStatus fires only on an actual delta (Rule F).
    private var lastStatus: SyncStatus? = null

    /**
     * Drains every pending op for [accountId] to the remote, then pulls remote changes back.
     *
     * Push outcomes per op: [PushResult.Accepted] marks the op synced (dropped from the queue) and
     * reports a [CardOpSucceeded] via [onAccept] (the store clears the card from `inFlight`);
     * [PushResult.Rejected] reconstructs the op's inverse and reports a [CardOpFailed] via [onReject]
     * then drops the op (the store reverts it); an [OfflineException] stops the drain with the queue
     * intact; a [TransientNetworkException] increments the op's attempt counter and moves on. After a
     * drain that pushed at least one op while still online, a [RemoteApi.pull] merges any remote changes
     * into the [LocalStore] and advances the sync cursor.
     *
     * @param accountId the account whose queue to drain.
     */
    public suspend fun drain(accountId: AccountId) {
        var online = true
        var lastError: String? = null
        var pushedAnything = false

        val pending = local.pendingOps(accountId)
        for (op in pending) {
            try {
                when (val result = remote.push(listOf(op))) {
                    is PushResult.Accepted -> {
                        local.markSynced(OpId(op.opId))
                        onAccept(CardOpSucceeded(opId = OpId(op.opId), cardId = CardId(op.cardId)))
                        pushedAnything = true
                    }

                    is PushResult.Rejected -> {
                        onReject(
                            CardOpFailed(
                                opId = OpId(result.opId),
                                cardId = CardId(op.cardId),
                                error = result.reason,
                                inverse = op.inverse.toDomain(),
                            ),
                        )
                        // Drop the rejected op — the store reverts it via the inverse; never retried.
                        local.markSynced(OpId(op.opId))
                        pushedAnything = true
                    }
                }
            } catch (offline: OfflineException) {
                online = false
                lastError = offline.message
                break
            } catch (transient: TransientNetworkException) {
                local.incrementAttempts(OpId(op.opId))
                lastError = transient.message
                // Keep the op queued; move on to give the next op a chance this drain.
            }
        }

        if (pushedAnything && online) {
            val page = remote.pull(local.lastSyncedAt(accountId))
            if (page.changes.isNotEmpty()) {
                local.applyRemote(page.changes)
                local.setLastSyncedAt(accountId, page.cursor)
            }
        }

        emitStatus(accountId, online = online, lastError = lastError)
    }

    /**
     * Schedules a [drain] for [accountId] on [scope], serialized so only one drain runs at a time.
     *
     * @param accountId the account whose queue to drain.
     * @return the launched [Job] (so callers / tests can join it).
     */
    public fun kick(accountId: AccountId): Job = scope.launch {
        drainLock.withLock { drain(accountId) }
    }

    private suspend fun emitStatus(accountId: AccountId, online: Boolean, lastError: String?) {
        val status = SyncStatus(
            online = online,
            pendingCount = local.pendingOps(accountId).size,
            inFlight = persistentSetOf(),
            lastSyncedAt = local.lastSyncedAt(accountId),
            lastError = lastError,
        )
        // Rule F: only emit on a real delta — no notification churn on a no-op drain.
        if (status != lastStatus) {
            lastStatus = status
            onStatus(status)
        }
    }
}

/**
 * The projected sync state the effects layer folds into `SyncModel`.
 *
 * @property online whether the last drain reached the backend (false once an [OfflineException] is hit).
 * @property pendingCount queued outbound ops not yet synced.
 * @property inFlight cards with an op currently in flight (drives the per-card optimistic alpha).
 * @property lastSyncedAt the last successful sync time, or null.
 * @property lastError the most recent connectivity / transient error message, or null.
 */
public data class SyncStatus(
    public val online: Boolean,
    public val pendingCount: Int,
    public val inFlight: PersistentSet<CardId>,
    public val lastSyncedAt: Instant?,
    public val lastError: String?,
)
