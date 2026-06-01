package org.reduxkotlin.devtools.wire

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Identity fields stamped on every app→monitor message. */
internal data class MessageContext(
    val socketId: String?,
    val name: String,
    val instanceId: String,
)

private fun MessageContext.envelope(type: String): MutableMap<String, JsonElement> = mutableMapOf(
    "type" to JsonPrimitive(type),
    "id" to (socketId?.let { JsonPrimitive(it) } ?: JsonNull),
    "name" to JsonPrimitive(name),
    "instanceId" to JsonPrimitive(instanceId),
)

/** Builds an `ACTION` message; [performAction] is double-encoded into the `action` string field. */
internal fun actionMessage(
    ctx: MessageContext,
    performAction: JsonElement,
    nextActionId: Int,
    isExcess: Boolean,
): JsonObject {
    val map = ctx.envelope("ACTION")
    map["action"] = JsonPrimitive(performAction.toString())
    map["nextActionId"] = JsonPrimitive(nextActionId)
    map["isExcess"] = JsonPrimitive(isExcess)
    return JsonObject(map)
}

/** Builds a `STATE` message; [liftedState] is double-encoded into the `payload` string field. */
internal fun stateMessage(ctx: MessageContext, liftedState: JsonElement): JsonObject {
    val map = ctx.envelope("STATE")
    map["payload"] = JsonPrimitive(liftedState.toString())
    return JsonObject(map)
}

/** Builds a `START` message (sent on connect). */
internal fun startMessage(ctx: MessageContext): JsonObject = JsonObject(ctx.envelope("START"))

/** Builds a `STOP` message. */
internal fun stopMessage(ctx: MessageContext): JsonObject = JsonObject(ctx.envelope("STOP"))
