package org.reduxkotlin.cli

import kotlin.system.exitProcess

/** Entry point for the unified `rk` CLI. */
public fun main(args: Array<String>) {
    rkCommand().main(args)
    // Skiko/Compose desktop keep non-daemon threads alive; force a clean exit after the command.
    exitProcess(0)
}
