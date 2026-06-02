package org.reduxkotlin.devtools.remote

import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.DevToolsSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteOutputTest {

    @Test
    fun has_stable_identity_and_is_off_by_default() {
        val out = RemoteOutput(RemoteConfig())
        assertEquals("remote", out.id)
        assertFalse(out.isRunning)
    }

    @Test
    fun start_then_stop_toggles_running_state() {
        val out = RemoteOutput(RemoteConfig(startEnabled = false))
        val session = DevToolsSession.create(DevToolsConfig(name = "t"))
        out.start(session)
        assertTrue(out.isRunning)
        out.stop()
        assertFalse(out.isRunning)
        session.close()
    }
}
