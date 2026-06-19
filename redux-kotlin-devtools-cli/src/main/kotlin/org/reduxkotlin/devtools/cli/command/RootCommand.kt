package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/** The `devtools` command group; dispatches to its subcommands. */
internal class DevToolsRootCommand : CliktCommand(name = "devtools") {
    override fun run() = Unit
}

/** Builds the `devtools` command group used by the unified `rk` CLI. */
public fun devToolsCommand(): CliktCommand = DevToolsRootCommand().subcommands(
    ServeCommand(),
    StoresCommand(),
    ActionsCommand(),
    DiffCommand(),
    StateCommand(),
    TailCommand(),
)
