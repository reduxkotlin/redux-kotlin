package org.reduxkotlin.snapshot.cli

import org.reduxkotlin.snapshot.demoSnapshots
import kotlin.system.exitProcess

/**
 * Default entry point renders the built-in demo registry. Real apps define their own `main`
 * calling `snapshotCommand(yourRegistry).main(args)`.
 */
public fun main(args: Array<String>) {
    snapshotCommand(demoSnapshots).main(args)
    // Skiko/Compose desktop leave non-daemon threads alive; force a clean exit after success.
    exitProcess(0)
}
