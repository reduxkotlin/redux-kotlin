package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement
import org.reduxkotlin.devtools.bridge.bridgeJson
import org.reduxkotlin.devtools.cli.capture.prettyJson
import org.reduxkotlin.devtools.cli.capture.readCapture

/** `state` — print the full post-state after the latest matching action (or --at <actionId>). */
internal class StateCommand : CliktCommand(name = "state") {
    private val q by QueryOptions()
    private val at by option("--at", help = "actionId to inspect (default: latest)").int()

    override fun run() {
        val ref = resolveStore(q.dir(), q.store)
        val actions = q.spec().apply(readCapture(ref.file).second)
        val target = if (at != null) actions.firstOrNull { it.actionId == at } else actions.lastOrNull()
        if (target == null) {
            echo("no matching action", err = true)
            return
        }
        val json = if (q.prettyEnabled()) prettyJson else bridgeJson
        echo(json.encodeToString(JsonElement.serializer(), target.state))
    }
}
