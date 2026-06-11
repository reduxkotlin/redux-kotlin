package org.reduxkotlin.devtools.ui.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DevToolsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionLogTest {

    private fun action(id: Int, type: String, ts: Long, payload: Int = id) = DevToolsEvent.ActionRecorded(
        actionId = id,
        action = buildJsonObject {
            put("type", type)
            put("amount", payload)
        },
        state = buildJsonObject { put("n", id) },
        diff = emptyList(),
        timestampMillis = ts,
        isExcess = false,
    )

    private fun model(vararg evs: DevToolsEvent.ActionRecorded): InAppModel =
        InAppModel(maxActions = 50).apply { evs.forEach { submit(it) } }

    @Test
    fun single_mode_picks_active_store() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.selectAll()
        // selectAll → merged; but single resolution is exercised by focusing one store.
        reg.focus("b")
        val rows = reg.state.value.actionLogRows(activeStoreId = "b")
        assertEquals(listOf("b"), rows.map { it.storeId })
        assertFalse(rows.first().merged)
        assertEquals("B1", actionType(rows.first().event.action))
    }

    @Test
    fun single_mode_falls_back_to_first_selected_when_active_not_selected() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.focus("a")
        // activeStoreId points at a store that is NOT selected → fall back to first selected ("a").
        val rows = reg.state.value.actionLogRows(activeStoreId = "b")
        assertEquals(listOf("a"), rows.map { it.storeId })
    }

    @Test
    fun merged_mode_returns_all_stores_time_sorted() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10), action(2, "A2", 30)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.selectAll()
        val rows = reg.state.value.actionLogRows(activeStoreId = null)
        assertTrue(rows.all { it.merged })
        assertEquals(
            listOf("a" to "A1", "b" to "B1", "a" to "A2"),
            rows.map { it.storeId to actionType(it.event.action) },
        )
    }

    @Test
    fun inapp_state_overload_maps_single_store_rows() {
        val state = InAppState(actions = listOf(action(1, "X", 5), action(2, "Y", 7)))
        val rows = state.actionLogRows(storeId = "s", storeName = "Session")
        assertEquals(listOf("s", "s"), rows.map { it.storeId })
        assertEquals(listOf("Session", "Session"), rows.map { it.storeName })
        assertTrue(rows.none { it.merged })
    }

    @Test
    fun matches_blank_query_matches_all() {
        val row = ActionLogRow("a", "Store A", false, action(1, "AddCard", 10))
        assertTrue(row.matches("", regex = false))
        assertTrue(row.matches("   ", regex = true))
    }

    @Test
    fun matches_plain_contains_across_type_payload_and_store() {
        val row = ActionLogRow("a", "Store A", false, action(7, "AddCard", 10, payload = 42))
        assertTrue(row.matches("addcard", regex = false))
        assertTrue(row.matches("amount", regex = false)) // payload preview
        assertTrue(row.matches("Store A", regex = false)) // store name
        assertFalse(row.matches("nope", regex = false))
    }

    @Test
    fun matches_regex_and_bad_regex_is_false() {
        val row = ActionLogRow("a", "Store A", false, action(1, "AddCard", 10))
        assertTrue(row.matches("Add.*ard", regex = true))
        assertFalse(row.matches("(", regex = true)) // malformed regex → false
    }

    @Test
    fun payload_preview_strips_type_and_name_keys() {
        val preview = payloadPreview(action(1, "AddCard", 10, payload = 5))
        assertTrue(preview.contains("amount"))
        assertFalse(preview.contains("type"))
        assertEquals("", payloadPreview(action(2, "Init", 10).copy(action = buildJsonObject { put("type", "Init") })))
    }
}
