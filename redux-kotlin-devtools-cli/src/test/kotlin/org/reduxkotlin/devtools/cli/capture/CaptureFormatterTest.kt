package org.reduxkotlin.devtools.cli.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DiffEntry
import org.reduxkotlin.devtools.DiffOp
import org.reduxkotlin.devtools.bridge.BridgeMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CaptureFormatterTest {
    private val action = BridgeMessage.Action(
        actionId = 7,
        action = buildJsonObject { put("type", JsonPrimitive("AddCard")) },
        state = buildJsonObject { put("count", JsonPrimitive(1)) },
        diff = listOf(DiffEntry(DiffOp.CHANGED, "count", JsonPrimitive(0), JsonPrimitive(1))),
        timestampMillis = 123L,
        isExcess = false,
    )

    @Test
    fun actions_tier_omits_diff_and_state() {
        val line = formatRecord(action, Format.ACTIONS, store = "taskflow::root")
        val obj = Json.parseToJsonElement(line).jsonObject
        assertEquals(setOf("actionId", "type", "store", "ts"), obj.keys)
    }

    @Test
    fun diff_tier_adds_diff_only() {
        val obj = Json.parseToJsonElement(formatRecord(action, Format.DIFF, "s")).jsonObject
        assertTrue("diff" in obj.keys && "state" !in obj.keys)
    }

    @Test
    fun full_tier_adds_state() {
        val obj = Json.parseToJsonElement(formatRecord(action, Format.FULL, "s")).jsonObject
        assertTrue("diff" in obj.keys && "state" in obj.keys)
    }

    @Test
    fun isExcess_true_emits_isExcess_key() {
        val excessAction = action.copy(isExcess = true)
        val obj = Json.parseToJsonElement(formatRecord(excessAction, Format.ACTIONS, "s")).jsonObject
        assertTrue("isExcess" in obj.keys)
        assertEquals(true, obj["isExcess"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())
    }

    @Test
    fun pretty_true_produces_multiline_output() {
        val prettyResult = formatRecord(action, Format.FULL, "s", pretty = true)
        val compactResult = formatRecord(action, Format.FULL, "s", pretty = false)
        assertTrue('\n' in prettyResult)
        assertFalse('\n' in compactResult)
    }
}
