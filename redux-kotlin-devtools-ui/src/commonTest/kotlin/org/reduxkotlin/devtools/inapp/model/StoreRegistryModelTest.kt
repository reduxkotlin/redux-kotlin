package org.reduxkotlin.devtools.inapp.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DevToolsEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreRegistryModelTest {

    private fun action(id: Int, type: String, ts: Long) = DevToolsEvent.ActionRecorded(
        actionId = id,
        action = buildJsonObject { put("type", type) },
        state = buildJsonObject { put("n", id) },
        diff = emptyList(),
        timestampMillis = ts,
        isExcess = false,
    )

    private fun model(vararg evs: DevToolsEvent.ActionRecorded): InAppModel =
        InAppModel(maxActions = 50).apply { evs.forEach { submit(it) } }

    @Test
    fun registers_stores_and_lists_them() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        assertEquals(listOf("a", "b"), reg.state.value.stores.map { it.ref.id })
    }

    @Test
    fun all_mode_merges_actions_by_timestamp_with_store_tags() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10), action(2, "A2", 30)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.selectAll()
        val rows = reg.state.value.mergedRows
        assertEquals(listOf("a" to "A1", "b" to "B1", "a" to "A2"), rows.map { it.storeId to actionTypeOf(it) })
    }

    @Test
    fun subset_filters_to_selected_stores() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.select(setOf("b"))
        assertEquals(listOf("b"), reg.state.value.mergedRows.map { it.storeId })
    }

    @Test
    fun one_mode_shows_a_single_store_and_marks_not_merged() {
        val reg = StoreRegistryModel()
        reg.put(StoreRef("a", "Store A"), model(action(1, "A1", 10)))
        reg.put(StoreRef("b", "Store B"), model(action(1, "B1", 20)))
        reg.focus("a")
        val s = reg.state.value
        assertEquals(setOf("a"), s.selectedIds)
        assertEquals(false, s.merged)
        assertEquals(listOf("a"), s.mergedRows.map { it.storeId })
    }
}

private fun actionTypeOf(row: StoreActionRow): String =
    (row.event.action as kotlinx.serialization.json.JsonObject)["type"].toString().trim('"')
