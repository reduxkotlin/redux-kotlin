package org.reduxkotlin.devtools.cli.capture

import org.reduxkotlin.devtools.bridge.BridgeMessage

/**
 * Converts a glob pattern (only `*` wildcard supported) to a [Regex].
 *
 * Each `*` maps to `.*` (zero or more characters), anchored `^...$`.
 * So `*Card*` matches any string containing "Card" (including `CardOpFailed`),
 * and a plain `Foo` (no `*`) is an exact match.
 */
internal fun globToRegex(glob: String): Regex {
    val segments = glob.split("*")
    val pattern = buildString {
        append('^')
        segments.forEachIndexed { index, segment ->
            if (index > 0) append(".*")
            append(Regex.escape(segment))
        }
        append('$')
    }
    return Regex(pattern)
}

/** Returns true when [a] satisfies all id/timestamp bounds in this [QuerySpec]. */
internal fun QuerySpec.matchesBounds(a: BridgeMessage.Action): Boolean = (sinceId == null || a.actionId >= sinceId) &&
        (untilId == null || a.actionId <= untilId) &&
        (sinceTs == null || a.timestampMillis >= sinceTs) &&
        (untilTs == null || a.timestampMillis <= untilTs)

/** A filter over captured actions. `type` is a glob (`*` wildcard); id/time bounds are inclusive. */
internal data class QuerySpec(
    val type: String? = null,
    val sinceId: Int? = null,
    val untilId: Int? = null,
    val sinceTs: Long? = null,
    val untilTs: Long? = null,
    val last: Int? = null,
) {
    /** Apply the filter, preserving capture order; `last` truncates to the final N after filtering. */
    fun apply(actions: List<BridgeMessage.Action>): List<BridgeMessage.Action> {
        val rx = type?.let { globToRegex(it) }
        val filtered = actions.filter { a ->
            (rx == null || rx.matches(actionType(a.action))) && matchesBounds(a)
        }
        return if (last != null && last < filtered.size) filtered.takeLast(last) else filtered
    }
}
