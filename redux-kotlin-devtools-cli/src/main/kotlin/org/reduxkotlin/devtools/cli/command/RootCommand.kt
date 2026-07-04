package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/** The `devtools` command group; dispatches to its subcommands. */
internal class DevToolsRootCommand : CliktCommand(name = "devtools") {
    override fun run() = Unit
}

/**
 * Builds the `devtools` command group used by the unified `rk` CLI.
 *
 * This is a library aggregator: it wires together the six `rk devtools` subcommands —
 * `serve` (start the bridge receiver and write `.jsonl` captures),
 * `stores` (list captured stores),
 * `actions` (query the action log),
 * `diff` (per-field JSON diffs per action),
 * `state` (full state snapshot at a given actionId), and
 * `tail` (stream recent/live actions).
 *
 * The returned [CliktCommand] is mounted as a subcommand by `redux-kotlin-cli`'s root `rk` command.
 * Consumers that build their own CLI tool can mount it the same way.
 */
public fun devToolsCommand(): CliktCommand = DevToolsRootCommand().subcommands(
    ServeCommand(),
    StoresCommand(),
    ActionsCommand(),
    DiffCommand(),
    StateCommand(),
    TailCommand(),
)
