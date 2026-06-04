package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.bridge.BridgeMessage
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CaptureQueryTest {
    private fun a(id: Int, type: String, ts: Long = id.toLong()) = BridgeMessage.Action(
        actionId = id,
        action = buildJsonObject { put("type", JsonPrimitive(type)) },
        state = buildJsonObject {},
        diff = emptyList(),
        timestampMillis = ts,
        isExcess = false,
    )

    private val data = listOf(a(1, "AddCard"), a(2, "MoveCard"), a(3, "AddColumn"), a(4, "CardOpFailed"))

    @Test
    fun filters_by_type_glob() {
        // *Card* = zero-or-more + "Card" + zero-or-more → matches any string containing "Card"
        assertEquals(listOf(1, 2, 4), QuerySpec(type = "*Card*").apply(data).map { it.actionId })
        // Add* = "Add" then anything
        assertEquals(listOf(1, 3), QuerySpec(type = "Add*").apply(data).map { it.actionId })
        // exact match (no wildcard)
        assertEquals(listOf(2), QuerySpec(type = "MoveCard").apply(data).map { it.actionId })
    }

    @Test
    fun filters_by_since_until_id_and_last() {
        assertEquals(listOf(2, 3), QuerySpec(sinceId = 2, untilId = 3).apply(data).map { it.actionId })
        assertEquals(listOf(3, 4), QuerySpec(last = 2).apply(data).map { it.actionId })
    }
}
