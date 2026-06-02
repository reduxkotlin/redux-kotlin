package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDiffTest {

    @Test
    fun changed_leaf_is_reported_with_before_and_after() {
        val before = buildJsonObject {
            put("filter", "ALL")
            put("count", 1)
        }
        val after = buildJsonObject {
            put("filter", "DONE")
            put("count", 1)
        }

        val diff = diffJson(before, after)

        assertEquals(1, diff.size)
        val e = diff.single()
        assertEquals(DiffOp.CHANGED, e.op)
        assertEquals("filter", e.path)
        assertEquals(JsonPrimitive("ALL"), e.before)
        assertEquals(JsonPrimitive("DONE"), e.after)
    }

    @Test
    fun added_and_removed_keys_are_reported() {
        val before = buildJsonObject { put("a", 1) }
        val after = buildJsonObject { put("b", 2) }

        val ops = diffJson(before, after).associate { it.path to it.op }

        assertEquals(DiffOp.REMOVED, ops["a"])
        assertEquals(DiffOp.ADDED, ops["b"])
    }

    @Test
    fun nested_object_paths_are_dotted() {
        val before = buildJsonObject { put("user", buildJsonObject { put("name", "ann") }) }
        val after = buildJsonObject { put("user", buildJsonObject { put("name", "bob") }) }

        assertEquals("user.name", diffJson(before, after).single().path)
    }

    @Test
    fun identical_states_produce_no_diff() {
        val s = buildJsonObject { put("x", 1) }
        assertEquals(emptyList(), diffJson(s, s))
    }
}
