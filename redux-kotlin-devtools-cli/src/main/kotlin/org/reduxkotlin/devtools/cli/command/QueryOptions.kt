package org.reduxkotlin.devtools.cli.command

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
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
import java.time.Instant

/** Default capture directory (relative to the working dir), shared by every subcommand. */
internal const val DEFAULT_CAPTURE_DIR: String = ".rk-devtools"

/** Parse a time as epoch millis (`1718000000000`) or an ISO-8601 instant (`2026-06-10T12:00:00Z`). */
internal fun parseTimeMillis(raw: String): Long = raw.toLongOrNull() ?: Instant.parse(raw).toEpochMilli()

/**
 * Pick the target store: the only one if unambiguous, or the one matching [key].
 * Throws [UsageError] (one-line CLI error, no stack trace) when resolution fails.
 */
internal fun resolveStore(dir: File, key: String?): StoreRef {
    val stores = discoverStores(dir)
    val problem = when {
        stores.isEmpty() -> "no captures found in ${dir.path} (is `rk-devtools serve` running?)"

        key != null && stores.none { it.key == key } ->
            "store '$key' not found; available: ${stores.joinToString { it.key }}"

        key == null && stores.size > 1 ->
            "multiple stores present; pass --store <key>: ${stores.joinToString { it.key }}"

        else -> null
    }
    if (problem != null) throw UsageError(problem)
    return if (key == null) stores.single() else stores.first { it.key == key }
}

/** Shared filter/format flags for query subcommands. */
internal class QueryOptions : OptionGroup() {
    val out by option("--out", help = "capture directory").default(DEFAULT_CAPTURE_DIR)
    val store by option("--store", help = "store key (clientId::storeInstanceId)")
    val type by option("--type", help = "action type glob, e.g. '*Card*'")
    val sinceId by option("--since", help = "min actionId").int()
    val untilId by option("--until", help = "max actionId").int()
    val sinceTime by option("--since-time", help = "min timestamp: epoch millis or ISO-8601 instant")
        .convert { raw ->
            runCatching { parseTimeMillis(raw) }
                .getOrElse { fail("expected epoch millis or an ISO-8601 instant (e.g. 2026-06-10T12:00:00Z)") }
        }
    val untilTime by option("--until-time", help = "max timestamp: epoch millis or ISO-8601 instant")
        .convert { raw ->
            runCatching { parseTimeMillis(raw) }
                .getOrElse { fail("expected epoch millis or an ISO-8601 instant (e.g. 2026-06-10T12:00:00Z)") }
        }
    val last by option("--last", help = "keep only the final N").int()

    // ignoreCase = true allows lowercase input (e.g. --format actions) in addition to ACTIONS
    val format by option("--format").enum<Format>(ignoreCase = true).default(Format.ACTIONS)
    val pretty by option("--pretty", help = "pretty-print JSON").flag()

    fun spec(): QuerySpec = QuerySpec(
        type = type,
        sinceId = sinceId,
        untilId = untilId,
        sinceTs = sinceTime,
        untilTs = untilTime,
        last = last,
    )

    fun dir(): File = File(out)
    fun prettyEnabled(): Boolean = pretty
}
