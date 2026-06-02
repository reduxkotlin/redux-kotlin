package org.reduxkotlin.devtools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineSessionTest {

    private data class St(val n: Int)

    @Test
    fun submitted_trace_is_emitted_with_the_matching_action_id() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "p"), dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }
        testScheduler.runCurrent()

        session.init(St(0))
        session.submitTrace(listOf(PipelineNodeTrace("mw_logger", 5, forwarded = true, changed = false)))
        session.record("Inc", St(1), session.takePendingTrace())
        testScheduler.advanceUntilIdle()
        session.close()
        job.cancel()

        val action = received.filterIsInstance<DevToolsEvent.ActionRecorded>().single()
        val traced = received.filterIsInstance<DevToolsEvent.PipelineTraced>().single()
        assertEquals(action.actionId, traced.trace.actionId)
        assertEquals("mw_logger", traced.trace.nodes.single().nodeId)
    }
}
