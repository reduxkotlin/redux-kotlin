package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runtime smoke test for the assembled `devtools` command tree.
 *
 * This guards against the class of breakage where the CLI compiles but the
 * tool is unusable — a subcommand dropped from the tree, the root command
 * throwing during construction, or `--help` failing at runtime. Because it
 * runs under the JVM `test` task (the task PR CI executes), it ALSO fails to
 * compile — and therefore fails CI — if the CLI main source set stops
 * compiling (e.g. a removed Compose dependency for the `--ui` path or a moved
 * monitor type). [EndToEndTest] covers capture/query logic but never exercises
 * the command layer.
 */
internal class CliSmokeTest {
    private val expectedSubcommands = listOf("serve", "stores", "actions", "diff", "state", "tail")

    @Test
    fun root_registers_every_expected_subcommand() {
        val names = devToolsCommand().registeredSubcommands().map { it.commandName }
        assertEquals(expectedSubcommands, names, "devtools subcommand tree changed")
    }

    @Test
    fun root_help_runs_and_lists_every_subcommand() {
        val result = devToolsCommand().test("--help")
        assertEquals(0, result.statusCode, "devtools --help should exit 0")
        expectedSubcommands.forEach { name ->
            assertTrue(name in result.output, "devtools --help omits subcommand: $name")
        }
    }

    @Test
    fun every_subcommand_help_is_reachable() {
        expectedSubcommands.forEach { name ->
            val result = devToolsCommand().test("$name --help")
            assertEquals(0, result.statusCode, "devtools $name --help should exit 0")
        }
    }
}
