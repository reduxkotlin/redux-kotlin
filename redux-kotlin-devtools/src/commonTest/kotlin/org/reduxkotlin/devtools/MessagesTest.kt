package org.reduxkotlin.devtools.wire

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesTest {
    private val ctx = MessageContext(socketId = "sock1", name = "MyStore", instanceId = "inst1")

    @Test
    fun actionMessageHasTypeDoubleEncodedActionAndStatePayload() {
        val performAction = JsonObject(mapOf("type" to JsonPrimitive("PERFORM_ACTION")))
        val state = JsonObject(mapOf("count" to JsonPrimitive(7)))
        val msg = actionMessage(ctx, performAction = performAction, state = state, nextActionId = 2, isExcess = false)
        assertEquals(JsonPrimitive("ACTION"), msg["type"])
        assertEquals(JsonPrimitive("sock1"), msg["id"])
        assertEquals(JsonPrimitive("MyStore"), msg["name"])
        assertEquals(JsonPrimitive(2), msg["nextActionId"])
        assertEquals(JsonPrimitive(false), msg["isExcess"])
        // action and payload must be STRINGS containing JSON, not nested objects
        assertEquals(JsonPrimitive(performAction.toString()), msg["action"])
        // payload carries the new state so the monitor's State/Diff panels update per action
        assertEquals(JsonPrimitive(state.toString()), msg["payload"])
    }

    @Test
    fun stateMessageDoubleEncodesPayload() {
        val lifted = JsonObject(mapOf("nextActionId" to JsonPrimitive(1)))
        val msg = stateMessage(ctx, liftedState = lifted)
        assertEquals(JsonPrimitive("STATE"), msg["type"])
        assertEquals(JsonPrimitive(lifted.toString()), msg["payload"])
    }

    @Test
    fun startMessageHasOnlyEnvelope() {
        val msg = startMessage(ctx)
        assertEquals(JsonPrimitive("START"), msg["type"])
        assertEquals(JsonPrimitive("inst1"), msg["instanceId"])
    }
}
