package org.reduxkotlin.devtools

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DevToolsHubTest {

    @AfterTest
    fun cleanup() = DevToolsHub.reset()

    @Test
    fun createSession_is_idempotent_per_instanceId() {
        val a = DevToolsHub.createSession(DevToolsConfig(name = "store"))
        val b = DevToolsHub.createSession(DevToolsConfig(name = "store"))
        assertSame(a, b)
        assertEquals(1, DevToolsHub.sessions().size)
    }

    @Test
    fun distinct_instanceIds_yield_distinct_sessions() {
        DevToolsHub.createSession(DevToolsConfig(name = "one"))
        DevToolsHub.createSession(DevToolsConfig(name = "two"))
        assertEquals(2, DevToolsHub.sessions().size)
    }

    @Test
    fun registerOutput_lists_the_output() {
        val out = object : DevToolsOutput {
            override val id = "remote"
            override val label = "Remote"
            override fun start(session: DevToolsSession) = Unit
            override fun stop() = Unit
        }
        DevToolsHub.registerOutput(out)
        assertTrue(DevToolsHub.outputs().any { it.id == "remote" })
    }

    @Test
    fun outputsFlow_publishes_registration_and_reset() {
        val out = object : DevToolsOutput {
            override val id = "remote"
            override val label = "Remote"
            override fun start(session: DevToolsSession) = Unit
            override fun stop() = Unit
        }
        assertTrue(DevToolsHub.outputsFlow.value.isEmpty())
        DevToolsHub.registerOutput(out)
        assertTrue(DevToolsHub.outputsFlow.value.any { it.id == "remote" })
        DevToolsHub.reset()
        assertTrue(DevToolsHub.outputsFlow.value.isEmpty())
    }

    @Test
    fun outputs_sharing_an_id_both_register_and_both_receive_events() = runTest {
        // BridgeOutput/RemoteOutput use constant ids ("bridge"/"remote") but are per-store
        // instances; deduping by id would silently drop every store's output after the first.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = DevToolsSession.create(DevToolsConfig(name = "dual"), dispatcher)
        val jobs = mutableListOf<Job>()

        fun recordingOutput(seen: MutableList<DevToolsEvent>) = object : DevToolsOutput {
            override val id = "bridge"
            override val label = "Bridge"
            override fun start(session: DevToolsSession) {
                jobs.add(launch(dispatcher) { session.events.toList(seen) })
            }

            override fun stop() = Unit
        }

        val seenA = mutableListOf<DevToolsEvent>()
        val seenB = mutableListOf<DevToolsEvent>()
        DevToolsHub.registerOutput(recordingOutput(seenA))
        DevToolsHub.registerOutput(recordingOutput(seenB))

        assertEquals(2, DevToolsHub.outputs().size)

        DevToolsHub.outputs().forEach { it.start(session) }
        testScheduler.runCurrent()
        session.init("state")
        testScheduler.advanceUntilIdle()

        assertTrue(seenA.any { it is DevToolsEvent.Initialized })
        assertTrue(seenB.any { it is DevToolsEvent.Initialized })
        session.close()
        jobs.forEach { it.cancel() }
    }

    @Test
    fun registering_the_same_output_instance_twice_lists_it_once() {
        val out = object : DevToolsOutput {
            override val id = "bridge"
            override val label = "Bridge"
            override fun start(session: DevToolsSession) = Unit
            override fun stop() = Unit
        }
        DevToolsHub.registerOutput(out)
        DevToolsHub.registerOutput(out)
        assertEquals(1, DevToolsHub.outputs().size)
    }

    @Test
    fun colliding_id_with_different_config_logs_a_warning() {
        val warnings = mutableListOf<String>()
        val logger: (String) -> Unit = { warnings.add(it) }
        // Same id ("dup"), different config (different maxAge) => collision warning, one session.
        DevToolsHub.createSession(DevToolsConfig(name = "dup", maxAge = 50, logger = logger))
        DevToolsHub.createSession(DevToolsConfig(name = "dup", maxAge = 10, logger = logger))
        assertEquals(1, DevToolsHub.sessions().size)
        assertTrue(warnings.any { it.contains("share devtools id") })
    }

    @Test
    fun removeSession_closes_and_drops_it() {
        DevToolsHub.createSession(DevToolsConfig(name = "gone"))
        DevToolsHub.removeSession("gone")
        assertTrue(DevToolsHub.sessions().isEmpty())
    }
}
