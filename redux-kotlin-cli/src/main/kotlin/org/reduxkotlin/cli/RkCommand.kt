package org.reduxkotlin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import org.reduxkotlin.devtools.cli.command.devToolsCommand
import org.reduxkotlin.snapshot.cli.snapshotCommand
import org.reduxkotlin.snapshot.demoSnapshots

/** Root `rk` command; groups the devtools and snapshot toolsets. */
internal class RkCommand : CliktCommand(name = "rk") {
    override fun run() = Unit
}

/** Builds the full `rk` command tree: `rk devtools …` and `rk snapshot …`. */
internal fun rkCommand(): CliktCommand = RkCommand()
    .versionOption(RK_VERSION, names = setOf("--version"), message = { "rk version $it" })
    .subcommands(
        devToolsCommand(),
        snapshotCommand(demoSnapshots),
    )
