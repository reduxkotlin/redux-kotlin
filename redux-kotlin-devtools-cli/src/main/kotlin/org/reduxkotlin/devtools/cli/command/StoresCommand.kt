package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import org.reduxkotlin.devtools.cli.capture.discoverStores
import java.io.File

/** `stores` — list the store keys present in the capture directory. */
internal class StoresCommand : CliktCommand(name = "stores") {
    private val out by option("--out").default(".rk-devtools")

    override fun run() = discoverStores(File(out)).forEach { echo("${it.key}\t${it.name}") }
}
