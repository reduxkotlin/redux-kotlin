package org.reduxkotlin.devtools.monitor

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import org.reduxkotlin.devtools.bridge.PROTOCOL_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals

class MonitorIngestTest {

    private fun hello(client: String, store: String) = BridgeMessage.Hello(
        protocolVersion = PROTOCOL_VERSION,
        clientId = client,
        clientLabel = "$client · test",
        storeInstanceId = store,
        storeName = store,
        serializerTier = "toString",
        token = null,
    )

    private fun action(id: Int, type: String, ts: Long) = BridgeMessage.Action(
        actionId = id,
        action = buildJsonObject { put("type", type) },
        state = buildJsonObject { put("n", id) },
        diff = emptyList(),
        timestampMillis = ts,
        isExcess = false,
    )

    @Test
    fun a_connection_registers_a_store_keyed_by_client_and_instance() {
        val ingest = MonitorIngest()
        val conn = ingest.openConnection()
        conn.accept(hello("tf", "TaskFlow-root"))
        conn.accept(BridgeMessage.Init(buildJsonObject { put("n", 0) }))
        conn.accept(action(1, "AddCard", 100))

        val stores = ingest.registry.state.value.stores
        assertEquals(1, stores.size)
        assertEquals("tf::TaskFlow-root", stores.single().ref.id)
        assertEquals("TaskFlow-root", stores.single().ref.name)
        assertEquals(listOf(1), stores.single().state.actions.map { it.actionId })
    }

    @Test
    fun two_stores_from_one_client_group_under_it() {
        val ingest = MonitorIngest()
        ingest.openConnection().apply {
            accept(hello("tf", "TaskFlow-root"))
            accept(action(1, "A", 10))
        }
        ingest.openConnection().apply {
            accept(hello("tf", "Account-2"))
            accept(action(1, "B", 20))
        }
        assertEquals(listOf("tf::TaskFlow-root", "tf::Account-2"), ingest.registry.state.value.stores.map { it.ref.id })
    }
}
