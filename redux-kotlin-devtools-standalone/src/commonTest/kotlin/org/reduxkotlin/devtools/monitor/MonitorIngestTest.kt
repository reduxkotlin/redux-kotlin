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
    fun re_hello_with_restarted_ids_preserves_both_sessions() {
        val ingest = MonitorIngest()
        ingest.openConnection().apply {
            accept(hello("tf", "TaskFlow-root"))
            accept(action(1, "A", 10))
            accept(action(2, "B", 20))
        }
        // App restarted: same store key, recorder ids reset to 1, new timestamps.
        ingest.openConnection().apply {
            accept(hello("tf", "TaskFlow-root"))
            accept(action(1, "C", 100))
            accept(action(2, "D", 200))
        }

        val (_, messages) = ingest.recordingFor("tf::TaskFlow-root")!!
        val captured = messages.filterIsInstance<BridgeMessage.Action>()
        assertEquals(listOf(1, 2, 3, 4), captured.map { it.actionId })
        assertEquals(listOf(10L, 20L, 100L, 200L), captured.map { it.timestampMillis })

        val store = ingest.registry.state.value.stores.single()
        assertEquals(listOf(1, 2, 3, 4), store.state.actions.map { it.actionId })
    }

    @Test
    fun re_hello_replay_of_same_actions_is_deduped() {
        val ingest = MonitorIngest()
        ingest.openConnection().apply {
            accept(hello("tf", "TaskFlow-root"))
            accept(action(1, "A", 10))
            accept(action(2, "B", 20))
        }
        // Reconnect within the same app session: the bridge reseeds history (same ids + timestamps)
        // before streaming new actions.
        ingest.openConnection().apply {
            accept(hello("tf", "TaskFlow-root"))
            accept(action(1, "A", 10))
            accept(action(2, "B", 20))
            accept(action(3, "C", 30))
        }

        val (_, messages) = ingest.recordingFor("tf::TaskFlow-root")!!
        assertEquals(listOf(1, 2, 3), messages.filterIsInstance<BridgeMessage.Action>().map { it.actionId })
        assertEquals(listOf(1, 2, 3), ingest.registry.state.value.stores.single().state.actions.map { it.actionId })
    }

    @Test
    fun re_hello_drops_the_new_sessions_init() {
        val ingest = MonitorIngest()
        ingest.openConnection().apply {
            accept(hello("tf", "TaskFlow-root"))
            accept(action(1, "A", 10))
        }
        ingest.openConnection().apply {
            accept(hello("tf", "TaskFlow-root"))
            accept(BridgeMessage.Init(buildJsonObject { put("n", 0) }))
            accept(action(1, "B", 100))
        }
        // The resumed Init neither wipes the model nor lands in the capture.
        val (_, messages) = ingest.recordingFor("tf::TaskFlow-root")!!
        assertEquals(0, messages.filterIsInstance<BridgeMessage.Init>().size)
        assertEquals(listOf(1, 2), ingest.registry.state.value.stores.single().state.actions.map { it.actionId })
    }

    @Test
    fun paused_ingest_drops_messages_until_resumed() {
        val ingest = MonitorIngest()
        val conn = ingest.openConnection()
        conn.accept(hello("tf", "TaskFlow-root"))
        conn.accept(action(1, "A", 10))
        ingest.paused = true
        conn.accept(action(2, "B", 20))
        ingest.paused = false
        conn.accept(action(3, "C", 30))

        val (_, messages) = ingest.recordingFor("tf::TaskFlow-root")!!
        assertEquals(listOf(1, 3), messages.filterIsInstance<BridgeMessage.Action>().map { it.actionId })
        assertEquals(listOf(1, 3), ingest.registry.state.value.stores.single().state.actions.map { it.actionId })
    }

    @Test
    fun clear_empties_the_capture_and_the_visible_log_but_keeps_the_store() {
        val ingest = MonitorIngest()
        val conn = ingest.openConnection()
        conn.accept(hello("tf", "TaskFlow-root"))
        conn.accept(action(1, "A", 10))

        ingest.clear("tf::TaskFlow-root")

        assertEquals(emptyList(), ingest.recordingFor("tf::TaskFlow-root")!!.second)
        val store = ingest.registry.state.value.stores.single()
        assertEquals(emptyList(), store.state.actions)

        // The live connection keeps appending after the clear.
        conn.accept(action(2, "B", 20))
        assertEquals(
            listOf(2),
            ingest.recordingFor("tf::TaskFlow-root")!!.second.filterIsInstance<BridgeMessage.Action>()
                .map { it.actionId },
        )
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
