package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/** Root `rk-devtools` command; dispatches to subcommands. */
internal class RootCommand : CliktCommand(name = "rk-devtools") {
    override fun run() = Unit
}

/** Build the configured command tree. */
internal fun rootCommand(): CliktCommand =
    RootCommand().subcommands(
        ServeCommand(),
        StoresCommand(),
        ActionsCommand(),
        DiffCommand(),
        StateCommand(),
        TailCommand(),
    )
