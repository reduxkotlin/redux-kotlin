package org.reduxkotlin.devtools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsSessionTest {

    private data class St(val n: Int)
    private object Inc
    private object Noise

    @Test
    fun init_then_record_emits_initialized_then_action_with_diff() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "s"), dispatcher)

        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }
        testScheduler.runCurrent()

        session.init(St(0))
        session.record(Inc, St(1))
        testScheduler.advanceUntilIdle()
        session.close()
        job.cancel()

        assertTrue(received[0] is DevToolsEvent.Initialized)
        val rec = received[1] as DevToolsEvent.ActionRecorded
        assertEquals(1, rec.actionId)
        // The only changed leaf is n: 0 -> 1, so exactly one CHANGED diff entry.
        assertEquals(DiffOp.CHANGED, rec.diff.single().op)
    }

    @Test
    fun denylisted_actions_are_not_recorded() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(denylist = listOf("Noise")), dispatcher)

        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }
        testScheduler.runCurrent()

        session.init(St(0))
        session.record(Noise, St(0))
        testScheduler.advanceUntilIdle()
        session.close()
        job.cancel()

        assertTrue(received.none { it is DevToolsEvent.ActionRecorded })
    }

    @Test
    fun recorded_action_carries_its_type_label() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "ty"), dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }
        testScheduler.runCurrent()

        session.init(St(0))
        session.record(Inc, St(1))
        testScheduler.advanceUntilIdle()
        session.close()
        job.cancel()

        val rec = received.filterIsInstance<DevToolsEvent.ActionRecorded>().single()
        val type = (rec.action as JsonObject)["type"]
        assertEquals("Inc", (type as JsonPrimitive).content)
    }
}
