package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.reduxkotlin.devtools.cli.capture.Format
import org.reduxkotlin.devtools.cli.capture.formatRecord
import org.reduxkotlin.devtools.cli.capture.readCapture

/** `actions` — list captured actions at the chosen --format tier. */
internal open class ActionsCommand(private val forced: Format? = null) :
    CliktCommand(name = if (forced == Format.DIFF) "diff" else "actions") {
    private val q by QueryOptions()

    override fun run() {
        val ref = resolveStore(q.dir(), q.store)
        val actions = q.spec().apply(readCapture(ref.file).second)
        val fmt = forced ?: q.format
        actions.forEach { echo(formatRecord(it, fmt, ref.key, q.prettyEnabled())) }
    }
}
