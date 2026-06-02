package org.reduxkotlin.devtools.inapp.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.devtools.DevToolsEvent
import org.reduxkotlin.devtools.PipelineNode
import org.reduxkotlin.devtools.PipelineNodeKind
import org.reduxkotlin.devtools.PipelineNodeTrace
import org.reduxkotlin.devtools.PipelineStructure
import org.reduxkotlin.devtools.PipelineTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InAppModelTest {

    private fun action(id: Int, type: String) = DevToolsEvent.ActionRecorded(
        actionId = id,
        action = buildJsonObject { put("type", type) },
        state = buildJsonObject { put("n", id) },
        diff = emptyList(),
        timestampMillis = id.toLong(),
        isExcess = false,
    )

    @Test
    fun actions_accumulate_and_dedupe_by_id() {
        val m = InAppModel(maxActions = 50)
        m.submit(action(1, "A"))
        m.submit(action(2, "B"))
        m.submit(action(1, "A"))
        assertEquals(listOf(1, 2), m.state.value.actions.map { it.actionId })
    }

    @Test
    fun seed_then_live_events_backfill_without_duplicates() {
        val m = InAppModel(maxActions = 50)
        m.seed(listOf(action(1, "A"), action(2, "B")))
        m.submit(action(2, "B"))
        m.submit(action(3, "C"))
        assertEquals(listOf(1, 2, 3), m.state.value.actions.map { it.actionId })
    }

    @Test
    fun selection_defaults_to_newest_until_user_selects() {
        val m = InAppModel(maxActions = 50)
        m.submit(action(1, "A"))
        m.submit(action(2, "B"))
        assertEquals(2, m.state.value.selected?.actionId)
        m.select(1)
        assertEquals(1, m.state.value.selected?.actionId)
    }

    @Test
    fun filter_narrows_by_type() {
        val m = InAppModel(maxActions = 50)
        m.submit(action(1, "AddTodo"))
        m.submit(action(2, "SetFilter"))
        m.setFilter("add")
        assertEquals(listOf(1), m.state.value.filteredActions.map { it.actionId })
    }

    @Test
    fun pipeline_structure_and_traces_are_stored() {
        val m = InAppModel(maxActions = 50)
        val structure = PipelineStructure(listOf(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY)))
        m.submit(DevToolsEvent.PipelineRegistered(structure))
        m.submit(DevToolsEvent.PipelineTraced(PipelineTrace(1, listOf(PipelineNodeTrace("dispatch", 5, true, false)))))
        assertEquals(structure, m.state.value.structure)
        assertTrue(m.state.value.tracesById.containsKey(1))
    }

    @Test
    fun maxActions_bounds_the_log() {
        val m = InAppModel(maxActions = 2)
        m.submit(action(1, "A"))
        m.submit(action(2, "B"))
        m.submit(action(3, "C"))
        assertEquals(listOf(2, 3), m.state.value.actions.map { it.actionId })
    }
}
