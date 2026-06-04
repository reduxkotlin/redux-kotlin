package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement
import org.reduxkotlin.devtools.bridge.bridgeJson
import org.reduxkotlin.devtools.cli.capture.readCapture
import java.io.File

/** `state` — print the full post-state after the latest action (or --at <actionId>). */
internal class StateCommand : CliktCommand(name = "state") {
    private val out by option("--out").default(".rk-devtools")
    private val store by option("--store")
    private val at by option("--at").int()

    override fun run() {
        val ref = resolveStore(File(out), store)
        val actions = readCapture(ref.file).second
        val target = if (at != null) actions.firstOrNull { it.actionId == at } else actions.lastOrNull()
        if (target == null) {
            echo("no matching action", err = true)
            return
        }
        echo(bridgeJson.encodeToString(JsonElement.serializer(), target.state))
    }
}
