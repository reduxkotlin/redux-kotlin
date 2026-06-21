package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the public [devToolsCommand] builder produces a correctly named and wired command group. */
class DevToolsCommandTest {
    /** Asserts the command group is registered under the name `devtools`. */
    @Test fun group_is_named_devtools() {
        assertEquals("devtools", devToolsCommand().commandName)
    }

    /** Asserts that `--help` output lists all expected subcommands. */
    @Test fun help_lists_subcommands() {
        val out = devToolsCommand().test("--help").output
        listOf("serve", "stores", "actions", "diff", "state", "tail").forEach {
            assertTrue(it in out, "devtools --help missing subcommand: $it")
        }
    }
}
