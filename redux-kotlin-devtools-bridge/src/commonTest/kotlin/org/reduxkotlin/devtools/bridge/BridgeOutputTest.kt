package org.reduxkotlin.devtools.bridge

import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeOutputTest {

    @Test
    fun stable_id_and_off_by_default() {
        val out = BridgeOutput(BridgeConfig())
        assertEquals("bridge", out.id)
        assertFalse(out.isRunning)
        assertFalse(out.startEnabled)
    }

    @Test
    fun start_then_stop_toggles_running() {
        val out = BridgeOutput(BridgeConfig(startEnabled = false))
        val session = DevToolsSession.create(DevToolsConfig(name = "t"))
        out.start(session)
        assertTrue(out.isRunning)
        out.stop()
        assertFalse(out.isRunning)
        session.close()
    }
}
