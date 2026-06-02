package org.reduxkotlin.devtools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineRecorderTest {

    @Test
    fun begin_node_commit_produces_a_trace_in_order() {
        val r = PipelineRecorder()
        assertFalse(r.isActive)
        r.begin()
        assertTrue(r.isActive)
        r.node("mw_logger", durationNanos = 10, forwarded = true, changed = false)
        r.node("slice_todos", durationNanos = 20, forwarded = true, changed = true)
        val trace = r.commit(actionId = 7)
        assertEquals(7, trace?.actionId)
        assertEquals(listOf("mw_logger", "slice_todos"), trace?.nodes?.map { it.nodeId })
        assertFalse(r.isActive)
    }

    @Test
    fun nested_begin_commits_lifo_and_routes_nodes_to_top() {
        val r = PipelineRecorder()
        r.begin()
        r.node("mw_a", 1, true, false)
        r.begin()
        r.node("mw_b", 1, true, false)
        val inner = r.commit(actionId = 2)
        r.node("slice_a", 1, true, true)
        val outer = r.commit(actionId = 1)
        assertEquals(listOf("mw_b"), inner?.nodes?.map { it.nodeId })
        assertEquals(listOf("mw_a", "slice_a"), outer?.nodes?.map { it.nodeId })
    }

    @Test
    fun node_without_begin_is_ignored_and_commit_is_null() {
        val r = PipelineRecorder()
        r.node("orphan", 1, true, false)
        assertNull(r.commit(actionId = 1))
    }
}
