package org.reduxkotlin.sample.taskflow.snapshot

import org.reduxkotlin.snapshot.cli.runCli
import kotlin.system.exitProcess

/**
 * `snapshotUi` entry point — renders TaskFlow screens from seeded state via the rk-snapshot CLI.
 *   ./gradlew :examples:taskflow:composeApp:snapshotUi --args="--scene board --preset seeded --out board.png"
 */
public fun main(args: Array<String>) {
    taskFlowSnapshots.runCli(args)
    // Skiko/Compose desktop leave non-daemon threads alive; force a clean exit after success.
    exitProcess(0)
}
