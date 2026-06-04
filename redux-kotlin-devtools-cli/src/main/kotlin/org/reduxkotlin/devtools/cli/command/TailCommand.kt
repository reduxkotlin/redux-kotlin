package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.reduxkotlin.devtools.cli.capture.formatRecord
import org.reduxkotlin.devtools.cli.capture.readCapture

/** `tail` — print recent actions; with --follow, poll the capture for new ones. */
internal class TailCommand : CliktCommand(name = "tail") {
    private val q by QueryOptions()
    private val follow by option("--follow", help = "poll for new actions continuously").flag()

    override fun run() {
        val ref = resolveStore(q.dir(), q.store)
        var lastId = -1
        fun pump() {
            val fresh = readCapture(ref.file).second.filter { it.actionId > lastId }
            q.spec().apply(fresh).forEach {
                lastId = maxOf(lastId, it.actionId)
                echo(formatRecord(it, q.format, ref.key, q.prettyEnabled()))
            }
        }
        pump()
        while (follow) {
            Thread.sleep(POLL_MS)
            runCatching { pump() }
        }
    }

    private companion object {
        const val POLL_MS = 300L
    }
}
