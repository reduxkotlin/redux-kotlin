package org.reduxkotlin.devtools.monitor

import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.bridge.BridgeMessage
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

    /** Opens a logical connection (one per bridge client/store). */
    public fun openConnection(): Connection = Connection()

    /** One connection's decode state: the first [BridgeMessage.Hello] binds it to a store. */
    public inner class Connection {
        private var model: InAppModel? = null

        /** Feed one decoded wire message. The first must be a [BridgeMessage.Hello]. */
        public fun accept(message: BridgeMessage) {
            when (message) {
                is BridgeMessage.Hello -> {
                    val k = "${message.clientId}::${message.storeInstanceId}"
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
}
