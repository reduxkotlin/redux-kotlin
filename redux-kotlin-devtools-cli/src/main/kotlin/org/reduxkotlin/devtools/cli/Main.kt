package org.reduxkotlin.devtools.cli

import org.reduxkotlin.devtools.cli.command.rootCommand

/** CLI entry point for the redux-kotlin DevTools tool. */
public fun main(args: Array<String>) {
    rootCommand().main(args)
}
