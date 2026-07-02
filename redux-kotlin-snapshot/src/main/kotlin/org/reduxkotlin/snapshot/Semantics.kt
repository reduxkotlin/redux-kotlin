package org.reduxkotlin.snapshot

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A deterministic, bounds-free dump of a rendered scene's semantics, beside the pixels.
 * Lets a consumer (e.g. an AI agent) assert content as text/JSON instead of reading the PNG.
 * Ordering is by semantics-node id (owners) and layout order (children) — never pixel position —
 * so the [toCanonicalJson] form is stable across architectures.
 */
public class SemanticsDump(
    /** One tree per Compose `SemanticsOwner` (a Popup/Dialog gets its own). Empty when nothing captured. */
    public val roots: List<Node>,
    /** All rendered text strings, pre-order, flattened. Kept for simple text assertions. */
    public val texts: List<String>,
) {
    /** One semantics node: its merged role/text/state plus merged children. Carries no bounds. */
    @Serializable
    public class Node(
        /** Accessibility role, lowercased (e.g. `button`), or null. */
        public val role: String?,
        /** Text on this node, in order (a merge boundary absorbs descendants' text). */
        public val text: List<String>,
        /** Content descriptions on this node, in order. */
        public val contentDescription: List<String>,
        /** Test tag, or null. */
        public val testTag: String?,
        /** `false` when the node is marked disabled; otherwise null (tri-state: never `true`). */
        public val enabled: Boolean?,
        /** Selected state, or null if unspecified. */
        public val selected: Boolean?,
        /** Toggleable state name (`On`/`Off`/`Indeterminate`), or null. */
        public val toggle: String?,
        /** Merged child nodes, in layout order. */
        public val children: List<Node>,
    )

    /** Compact indented tree — the default agent/human form. One node per line, 2-space indent per depth. */
    public fun toText(): String {
        if (roots.isEmpty()) return "(no semantics)"
        val sb = StringBuilder()
        roots.forEach { appendNode(it, 0, sb) }
        return sb.toString().trimEnd('\n')
    }

    /** The single JSON form: pretty, stable field order, no bounds. Used for `--semantics-format json` and goldens. */
    @OptIn(ExperimentalSerializationApi::class)
    public fun toCanonicalJson(): String = CANONICAL.encodeToString(ListSerializer(Node.serializer()), roots)

    private fun appendNode(n: Node, depth: Int, sb: StringBuilder) {
        val indent = "  ".repeat(depth)
        val fields = buildList {
            n.role?.let { add("role=$it") }
            if (n.text.isNotEmpty()) add("text=${n.text}")
            if (n.contentDescription.isNotEmpty()) add("desc=${n.contentDescription}")
            n.testTag?.let { add("testTag=$it") }
            n.enabled?.let { add("enabled=$it") }
            n.selected?.let { add("selected=$it") }
            n.toggle?.let { add("toggle=$it") }
        }
        sb.append(indent).append("node").append(if (fields.isEmpty()) "" else " " + fields.joinToString(" ")).append('\n')
        n.children.forEach { appendNode(it, depth + 1, sb) }
    }

    /** Shared instances. */
    @OptIn(ExperimentalSerializationApi::class)
    public companion object {
        /** An empty dump (no semantics captured). */
        public val EMPTY: SemanticsDump = SemanticsDump(roots = emptyList(), texts = emptyList())
        private val CANONICAL = Json { prettyPrint = true; encodeDefaults = true; prettyPrintIndent = "  " }
    }
}
