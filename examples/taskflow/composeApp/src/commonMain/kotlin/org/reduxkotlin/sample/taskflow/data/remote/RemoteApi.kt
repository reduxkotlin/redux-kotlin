package org.reduxkotlin.sample.taskflow.data.remote

import kotlinx.collections.immutable.PersistentList
import kotlin.time.Instant

/**
 * The network seam between the sync engine and a backend.
 *
 * This interface is deliberately **pure of any DB or Compose types** — it traffics only in
 * serializable [SyncOp]s (outbound) and domain-typed [RemoteChange]s (inbound). That keeps it
 * the single point a real HTTP client replaces behind the same offline-first sync flow; the
 * in-repo implementation [FakeRemoteApi] simulates latency / failure / an offline toggle.
 */
public interface RemoteApi {
    /**
     * Pushes a batch of queued outbound [ops] to the backend.
     *
     * Returns [PushResult.Accepted] when every op applied, or [PushResult.Rejected] for a
     * server-side validation conflict (e.g. a move into a full-WIP column). Connectivity /
     * transient problems are signalled by **throwing** [OfflineException] /
     * [TransientNetworkException] — the engine treats those as Deferred (retry), never a reject.
     *
     * @param ops the queued ops to push, oldest first.
     * @return the push outcome.
     */
    public suspend fun push(ops: List<SyncOp>): PushResult

    /**
     * Pulls the changes the backend has accumulated after [since].
     *
     * @param since the cursor from the previous pull (null pulls from the beginning).
     * @return a [RemotePage] of changes plus the new cursor; an empty page echoes [since].
     */
    public suspend fun pull(since: Instant?): RemotePage
}

/**
 * A page of remote changes returned by [RemoteApi.pull].
 *
 * @property changes the domain-typed changes after the requested cursor, oldest first.
 * @property cursor the new cursor to pass to the next [RemoteApi.pull] (echoes the request
 *   cursor when [changes] is empty).
 */
public data class RemotePage(public val changes: PersistentList<RemoteChange>, public val cursor: Instant?)
