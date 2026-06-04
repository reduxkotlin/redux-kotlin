package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import org.reduxkotlin.devtools.cli.capture.Format
import org.reduxkotlin.devtools.cli.capture.QuerySpec
import org.reduxkotlin.devtools.cli.capture.StoreRef
import org.reduxkotlin.devtools.cli.capture.discoverStores
import java.io.File

/** Default capture directory under the current working dir. */
internal fun defaultCaptureDir(): File = File(".rk-devtools")

/** Pick the target store: the only one if unambiguous, or the one matching [key]. */
internal fun resolveStore(dir: File, key: String?): StoreRef {
    val stores = discoverStores(dir)
    check(stores.isNotEmpty()) { "no captures found in ${dir.path} (is `rk-devtools serve` running?)" }
    if (key != null) {
        return stores.firstOrNull { it.key == key }
            ?: error("store '$key' not found; available: ${stores.joinToString { it.key }}")
    }
    check(stores.size == 1) { "multiple stores present; pass --store <key>: ${stores.joinToString { it.key }}" }
    return stores.first()
}

/** Shared filter/format flags for query subcommands. */
internal class QueryOptions : OptionGroup() {
    val out by option("--out", help = "capture directory").default(".rk-devtools")
    val store by option("--store", help = "store key (clientId::storeInstanceId)")
    val type by option("--type", help = "action type glob, e.g. '*Card*'")
    val sinceId by option("--since", help = "min actionId").int()
    val untilId by option("--until", help = "max actionId").int()
    val last by option("--last", help = "keep only the final N").int()

    // ignoreCase = true allows lowercase input (e.g. --format actions) in addition to ACTIONS
    val format by option("--format").enum<Format>(ignoreCase = true).default(Format.ACTIONS)
    val pretty by option("--pretty", help = "pretty-print JSON").flag()

    fun spec(): QuerySpec = QuerySpec(type = type, sinceId = sinceId, untilId = untilId, last = last)
    fun dir(): File = File(out)
    fun prettyEnabled(): Boolean = pretty
}
