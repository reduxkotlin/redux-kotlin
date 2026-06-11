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
    fun maxAge_exposes_the_configs_retention_bound() {
        val session = DevToolsSession.create(DevToolsConfig(name = "bounded", maxAge = 7))
        assertEquals(7, session.maxAge)
        session.close()
    }

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
    fun overflowing_the_capture_buffer_counts_drops_and_warns() = runTest {
        val logged = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "drop", logger = { logged.add(it) }), dispatcher)

        // The consumer coroutine never runs (scheduler is not advanced), so the 256-slot capture
        // buffer fills and DROP_OLDEST displaces the oldest pending captures.
        repeat(300) { session.record(Inc, St(it)) }

        assertTrue(
            logged.any { it.contains("dropped 1 captures") },
            "expected the first-drop warning, got: $logged",
        )
        session.close()
    }

    @Test
    fun history_respects_the_maxAge_bound_and_orders_newest_last() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "hist", maxAge = 3), dispatcher)

        session.init(St(0))
        repeat(5) { session.record(Inc, St(it + 1)) }
        testScheduler.advanceUntilIdle()

        // Bounded to maxAge, ascending actionIds, newest last.
        assertEquals(listOf(3, 4, 5), session.history().map { it.actionId })
        session.close()
    }

    @Test
    fun history_clears_when_the_session_is_reinitialized() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "hist2"), dispatcher)

        session.init(St(0))
        session.record(Inc, St(1))
        testScheduler.advanceUntilIdle()
        assertEquals(1, session.history().size)

        session.init(St(0))
        testScheduler.advanceUntilIdle()
        assertTrue(session.history().isEmpty())
        session.close()
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
