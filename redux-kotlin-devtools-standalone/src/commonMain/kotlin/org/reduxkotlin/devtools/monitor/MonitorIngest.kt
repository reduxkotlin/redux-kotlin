package org.reduxkotlin.devtools.monitor

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.RecordingHeader
import org.reduxkotlin.devtools.inapp.model.InAppModel
import org.reduxkotlin.devtools.inapp.model.StoreRef
import org.reduxkotlin.devtools.inapp.model.StoreRegistryModel

/**
 * Decodes bridge message streams into per-store [InAppModel]s and registers them in [registry].
 * A store's key is `clientId::storeInstanceId` (stable across reconnects). Pure — no sockets; the
 * server (jvm) and the web client feed [Connection.accept] with decoded [BridgeMessage]s.
 */
public class MonitorIngest {

    /** The aggregate view the UI renders. */
    public val registry: StoreRegistryModel = StoreRegistryModel()

    /** Per-store capture: Hello metadata + the accepted post-Hello messages, keyed by store id. */
    private val captures = mutableMapOf<String, Capture>()

    /**
     * Guards [captures] and every [Capture.messages] against concurrent access. Bridge clients run
     * on a multi-threaded IO dispatcher (root + account stores connect at once) and the Compose UI
     * thread reads via [recordingFor], so all access is serialized. No-op on single-threaded wasmJs.
     */
    private val lock = SynchronizedObject()

    /** A store's recording source: its [BridgeMessage.Hello] + bounded list of later messages. */
    private class Capture(val hello: BridgeMessage.Hello) {
        val messages: ArrayDeque<BridgeMessage> = ArrayDeque()
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

    /** One connection's decode state: the first [BridgeMessage.Hello] binds it to a store. */
    public inner class Connection {
        private var model: InAppModel? = null
        private var storeKey: String? = null

        /** Feed one decoded wire message. The first must be a [BridgeMessage.Hello]. */
        public fun accept(message: BridgeMessage) {
            val key = storeKey
            if (message !is BridgeMessage.Hello && key != null) {
                synchronized(lock) {
                    val cap = captures[key]
                    if (cap != null && cap.messages.size < MAX_CAPTURED) cap.messages.addLast(message)
                }
            }
            when (message) {
                is BridgeMessage.Hello -> {
                    val k = "${message.clientId}::${message.storeInstanceId}"
                    storeKey = k
                    synchronized(lock) { captures[k] = Capture(message) }
                    val m = InAppModel()
                    model = m
                    registry.put(StoreRef(k, message.storeName), m)
                }

                is BridgeMessage.Init -> emit(DevToolsEvent.Initialized(message.state))

                is BridgeMessage.Action -> emit(
                    DevToolsEvent.ActionRecorded(
                        message.actionId,
                        message.action,
                        message.state,
                        message.diff,
                        message.timestampMillis,
                        message.isExcess,
                    ),
                )

                is BridgeMessage.PipelineRegistered -> emit(DevToolsEvent.PipelineRegistered(message.structure))

                is BridgeMessage.PipelineTraced -> emit(DevToolsEvent.PipelineTraced(message.trace))

                is BridgeMessage.HelloAck -> Unit
            }
        }

        /** Marks the store disconnected — kept read-only in the registry (P0 leaves it registered). */
        public fun close() { /* P0: leave registered (read-only by construction) */ }

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
