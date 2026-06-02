package org.reduxkotlin.devtools

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
