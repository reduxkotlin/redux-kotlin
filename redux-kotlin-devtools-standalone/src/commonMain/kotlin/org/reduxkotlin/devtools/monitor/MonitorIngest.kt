package org.reduxkotlin.devtools.monitor

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.json.JsonNull
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.StoreRef
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel

/**
 * Decodes bridge message streams into per-store [InAppModel]s and registers them in [registry].
 * A store's key is `clientId::storeInstanceId` (stable across reconnects). Pure — no sockets; the
 * server (jvm) feeds [Connection.accept] with decoded [BridgeMessage]s.
 *
 * Reconnect semantics: a re-Hello for an already-known key RESUMES the existing capture and model
 * instead of replacing them. Replayed history (the bridge reseeds on reconnect) is deduped by
 * `(actionId, timestampMillis)`. If an action arrives whose id was already captured but whose
 * timestamp differs, the source app restarted and its recorder ids reset — the new session's ids
 * are then shifted past the highest captured id so prior entries are preserved and the log stays
 * monotonic. A resumed session's `Init` is dropped (it would wipe the preserved history).
 * Limitation: pipeline trace `actionId`s are not re-keyed, so traces from a restarted session may
 * attach to same-numbered actions of the previous one.
 */
public class MonitorIngest {

    /** The aggregate view the UI renders. */
    public val registry: StoreRegistryModel = StoreRegistryModel()

    /**
     * While `true`, incoming non-handshake messages are dropped on arrival (neither captured nor
     * shown). Hello/HelloAck still flow so connection bookkeeping stays consistent.
     */
    public var paused: Boolean = false

    /** Per-store capture: Hello metadata + the accepted post-Hello messages, keyed by store id. */
    private val captures = mutableMapOf<String, Capture>()

    /**
     * Guards [captures] and every [Capture]'s mutable state against concurrent access. Bridge
     * clients run on a multi-threaded IO dispatcher (root + account stores connect at once) and the
     * Compose UI thread reads via [recordingFor], so all access is serialized.
     */
    private val lock = SynchronizedObject()

    /** A store's recording source: its [BridgeMessage.Hello] + bounded list of later messages. */
    private class Capture(val hello: BridgeMessage.Hello) {
        val messages: ArrayDeque<BridgeMessage> = ArrayDeque()

        /** Highest actionId captured so far (after re-keying). */
        var maxActionId: Int = 0

        /**
         * `timestampMillis` per captured actionId — lets a resumed connection tell a reseed replay
         * (same id, same timestamp → drop) from an app restart (same id, new timestamp → re-key).
         */
        val actionTimestamps: MutableMap<Int, Long> = mutableMapOf()
    }

    /** Opens a logical connection (one per bridge client/store). */
    public fun openConnection(): Connection = Connection()

    /**
     * Builds a recording for store [storeId] (`clientId::storeInstanceId`) from its captured Hello
     * metadata + post-Hello messages, or `null` if the store has never sent a Hello.
     */
    public fun recordingFor(storeId: String): Pair<RecordingHeader, List<BridgeMessage>>? = synchronized(lock) {
        val cap = captures[storeId] ?: return@synchronized null
        val h = cap.hello
        val header = RecordingHeader(
            protocolVersion = h.protocolVersion,
            serializerTier = h.serializerTier,
            clientId = h.clientId,
            clientLabel = h.clientLabel,
            storeName = h.storeName,
            storeInstanceId = h.storeInstanceId,
        )
        header to cap.messages.toList()
    }

    /**
     * Clears the captured messages and the visible action log for [storeId], keeping the store
     * registered and its live connections attached. Dedupe/re-key bookkeeping is retained so a
     * later reconnect replay does not resurrect cleared entries.
     */
    public fun clear(storeId: String) {
        synchronized(lock) { captures[storeId]?.messages?.clear() }
        registry.modelFor(storeId)?.submit(DevToolsEvent.Initialized(JsonNull))
        registry.refresh()
    }

    /** One connection's decode state: the first [BridgeMessage.Hello] binds it to a store. */
    public inner class Connection {
        private var model: InAppModel? = null
        private var storeKey: String? = null

        /** `true` when the Hello matched an already-captured store (reconnect/restart). */
        private var resumed = false

        /** Added to incoming actionIds; becomes non-zero after an id collision (app restart). */
        private var idOffset = 0

        /** Feed one decoded wire message. The first must be a [BridgeMessage.Hello]. */
        public fun accept(message: BridgeMessage) {
            if (paused && message !is BridgeMessage.Hello) return
            when (message) {
                is BridgeMessage.Hello -> onHello(message)

                is BridgeMessage.HelloAck -> Unit

                is BridgeMessage.Init ->
                    // A resumed session's Init would wipe the preserved history — drop it.
                    if (!resumed) {
                        capture(message)
                        emit(DevToolsEvent.Initialized(message.state))
                    }

                is BridgeMessage.Action -> {
                    val rekeyed = rekeyAndCapture(message) ?: return
                    emit(
                        DevToolsEvent.ActionRecorded(
                            rekeyed.actionId,
                            rekeyed.action,
                            rekeyed.state,
                            rekeyed.diff,
                            rekeyed.timestampMillis,
                            rekeyed.isExcess,
                        ),
                    )
                }

                is BridgeMessage.PipelineRegistered -> {
                    capture(message)
                    emit(DevToolsEvent.PipelineRegistered(message.structure))
                }

                is BridgeMessage.PipelineTraced -> {
                    capture(message)
                    emit(DevToolsEvent.PipelineTraced(message.trace))
                }
            }
        }

        /** Marks the store disconnected — kept read-only in the registry (P0 leaves it registered). */
        public fun close() { /* P0: leave registered (read-only by construction) */ }

        private fun onHello(message: BridgeMessage.Hello) {
            val k = "${message.clientId}::${message.storeInstanceId}"
            storeKey = k
            synchronized(lock) {
                val existing = captures[k]
                if (existing == null) captures[k] = Capture(message)
                resumed = existing != null
                idOffset = 0
            }
            val m = registry.modelFor(k) ?: InAppModel().also { registry.put(StoreRef(k, message.storeName), it) }
            model = m
        }

        /**
         * Dedupes/re-keys [message] against the store's capture (see class KDoc for the reconnect
         * semantics) and appends it. Returns the message to emit, or `null` for a replay duplicate.
         * The capture list is bounded by [MAX_CAPTURED], but bookkeeping and emission continue past
         * the cap so the live view stays current.
         */
        private fun rekeyAndCapture(message: BridgeMessage.Action): BridgeMessage.Action? = synchronized(lock) {
            val cap = captures[storeKey] ?: return@synchronized message
            var id = message.actionId + idOffset
            val knownTs = cap.actionTimestamps[id]
            if (knownTs != null) {
                if (knownTs == message.timestampMillis) return@synchronized null // reseed replay
                // Same id, different content: the app restarted and its recorder ids reset.
                // Shift this connection's ids past everything captured so old entries survive.
                idOffset = cap.maxActionId + 1 - message.actionId
                id = message.actionId + idOffset
            }
            val rekeyed = if (id == message.actionId) message else message.copy(actionId = id)
            cap.maxActionId = maxOf(cap.maxActionId, id)
            cap.actionTimestamps[id] = rekeyed.timestampMillis
            if (cap.messages.size < MAX_CAPTURED) cap.messages.addLast(rekeyed)
            rekeyed
        }

        private fun capture(message: BridgeMessage) {
            val key = storeKey ?: return
            synchronized(lock) {
                val cap = captures[key]
                if (cap != null && cap.messages.size < MAX_CAPTURED) cap.messages.addLast(message)
            }
        }

        private fun emit(event: DevToolsEvent) {
            val m = model ?: return
            m.submit(event)
            registry.refresh()
        }
    }

    private companion object {
        /** Upper bound on per-store captured messages (oldest stay; new ones drop past the cap). */
        private const val MAX_CAPTURED = 5000
    }
}
