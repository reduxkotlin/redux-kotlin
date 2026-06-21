package org.reduxkotlin.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RkCommandTest {
    @Test fun root_is_named_rk() {
        assertEquals("rk", rkCommand().commandName)
    }

    @Test fun help_lists_both_groups() {
        val out = rkCommand().test("--help").output
        assertTrue("devtools" in out, "rk --help missing 'devtools'")
        assertTrue("snapshot" in out, "rk --help missing 'snapshot'")
    }

    @Test fun version_option_prints_a_version() {
        val out = rkCommand().test("--version").output
        assertTrue("rk version" in out, "rk --version output: $out")
    }
}
