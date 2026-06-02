package org.reduxkotlin.devtools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The kind of change a [DiffEntry] represents between two states. */
public enum class DiffOp {
    /** A path present in the new state but absent in the previous one. */
    ADDED,

    /** A path present in the previous state but absent in the new one. */
    REMOVED,

    /** A path present in both states whose leaf value differs. */
    CHANGED,
}

/**
 * A single difference between two serialized states at a dotted [path].
 *
 * @property op the kind of change.
 * @property path dotted path to the leaf (object keys and array indices joined by `.`).
 * @property before the previous value, or `null` for [DiffOp.ADDED].
 * @property after the new value, or `null` for [DiffOp.REMOVED].
 */
public data class DiffEntry(
    public val op: DiffOp,
    public val path: String,
    public val before: JsonElement?,
    public val after: JsonElement?,
)

/**
 * Computes the leaf-level differences between two serialized states.
 *
 * Objects and arrays are walked recursively; arrays are compared by index. The result is a flat,
 * order-stable list of added/removed/changed leaves suitable for rendering a per-action diff.
 */
public fun diffJson(before: JsonElement, after: JsonElement): List<DiffEntry> {
    val out = ArrayList<DiffEntry>()
    diffInto(before, after, "", out)
    return out
}

private fun childrenOf(element: JsonElement): Map<String, JsonElement>? = when (element) {
    is JsonObject -> element
    is JsonArray -> element.mapIndexed { i, v -> i.toString() to v }.toMap()
    else -> null
}

private fun join(prefix: String, key: String): String = if (prefix.isEmpty()) key else "$prefix.$key"

private fun diffInto(before: JsonElement, after: JsonElement, prefix: String, out: MutableList<DiffEntry>) {
    val b = childrenOf(before)
    val a = childrenOf(after)
    if (b == null || a == null) {
        if (before != after) out.add(DiffEntry(DiffOp.CHANGED, prefix, before, after))
        return
    }
    for ((key, bv) in b) {
        val path = join(prefix, key)
        val av = a[key]
        if (av == null) out.add(DiffEntry(DiffOp.REMOVED, path, bv, null)) else diffInto(bv, av, path, out)
    }
    for ((key, av) in a) {
        if (key !in b) out.add(DiffEntry(DiffOp.ADDED, join(prefix, key), null, av))
    }
}
