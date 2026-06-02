package org.reduxkotlin.devtools.bridge

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DevToolsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeProtocolTest {

    private fun encDec(m: BridgeMessage): BridgeMessage {
        val s = bridgeJson.encodeToString(BridgeMessage.serializer(), m)
        return bridgeJson.decodeFromString(BridgeMessage.serializer(), s)
    }

    @Test
    fun hello_round_trips() {
        val h = BridgeMessage.Hello(
            protocolVersion = PROTOCOL_VERSION,
            clientId = "c1",
            clientLabel = "TaskFlow · desktop",
            storeInstanceId = "store-1",
            storeName = "TaskFlow-root",
            serializerTier = "kotlinx.serialization",
            token = null,
        )
        assertEquals(h, encDec(h))
    }

    @Test
    fun action_event_maps_to_wire_and_round_trips() {
        val ev = DevToolsEvent.ActionRecorded(
            actionId = 5,
            action = buildJsonObject { put("type", "AddCard") },
            state = buildJsonObject { put("n", 5) },
            diff = emptyList(),
            timestampMillis = 123L,
            isExcess = false,
        )
        val wire = toWire(ev)
        assertTrue(wire is BridgeMessage.Action)
        val back = encDec(wire) as BridgeMessage.Action
        assertEquals(5, back.actionId)
        assertEquals(123L, back.timestampMillis)
        assertEquals(false, back.isExcess)
    }

    @Test
    fun initialized_maps_to_init() {
        val wire = toWire(DevToolsEvent.Initialized(buildJsonObject { put("n", 0) }))
        assertTrue(wire is BridgeMessage.Init)
    }
}
