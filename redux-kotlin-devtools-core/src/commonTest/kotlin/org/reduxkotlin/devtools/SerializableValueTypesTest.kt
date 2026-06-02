package org.reduxkotlin.devtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializableValueTypesTest {

    private val json = Json

    @Test
    fun diff_entry_round_trips() {
        val e = DiffEntry(DiffOp.CHANGED, "a.b", JsonPrimitive(1), JsonPrimitive(2))
        val s = json.encodeToString(DiffEntry.serializer(), e)
        assertEquals(e, json.decodeFromString(DiffEntry.serializer(), s))
    }

    @Test
    fun pipeline_trace_round_trips() {
        val t = PipelineTrace(
            actionId = 7,
            nodes = listOf(PipelineNodeTrace("mw_0_logger", 1234, forwarded = true, changed = false)),
        )
        val s = json.encodeToString(PipelineTrace.serializer(), t)
        assertEquals(t, json.decodeFromString(PipelineTrace.serializer(), s))
    }

    @Test
    fun pipeline_structure_round_trips() {
        val st = PipelineStructure(listOf(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY)))
        val s = json.encodeToString(PipelineStructure.serializer(), st)
        assertEquals(st, json.decodeFromString(PipelineStructure.serializer(), s))
    }
}
