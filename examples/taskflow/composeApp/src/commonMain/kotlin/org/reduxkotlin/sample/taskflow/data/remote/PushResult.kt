package org.reduxkotlin.sample.taskflow.data.remote

/**
 * The outcome of a [RemoteApi.push] that the backend actually evaluated.
 *
 * Connectivity / transient outcomes are NOT modelled here — they are thrown ([OfflineException] /
 * [TransientNetworkException]) and treated as Deferred by the engine. A [Rejected] result, by
 * contrast, is a deterministic server decision the engine reverts via the op's per-op inverse.
 */
public sealed interface PushResult {
    /** Every op in the batch applied cleanly on the server. */
    public data object Accepted : PushResult

    /**
     * The server refused an op (a validation conflict, e.g. a move into a full-WIP column).
     *
     * @property opId the offending op's id (so the engine can revert exactly that op).
     * @property reason a human-readable explanation surfaced to the user as a sync toast.
     */
    public data class Rejected(public val opId: String, public val reason: String) : PushResult
}

/**
 * Thrown by [RemoteApi] when the simulated (or real) backend is unreachable because connectivity
 * is off. The sync engine keeps the queue intact and retries on reconnect (Deferred).
 *
 * @param message a description of the offline condition.
 */
public class OfflineException(message: String = "Remote is offline") : Exception(message)

/**
 * Thrown by [RemoteApi] for a transient network failure (timeout / dropped request). The sync
 * engine bumps the attempt counter and retries with backoff (Deferred).
 *
 * @param message a description of the transient failure.
 */
public class TransientNetworkException(message: String = "Transient network failure") : Exception(message)
