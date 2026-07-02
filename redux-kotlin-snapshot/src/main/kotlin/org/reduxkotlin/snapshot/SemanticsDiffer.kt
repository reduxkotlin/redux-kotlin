package org.reduxkotlin.snapshot

/** Result of a semantics comparison: a lowercase [result] and a bounded, readable [delta]. */
public class SemanticsDiffResult(
    /** `match` when the canonical forms are equal, else `mismatch`. */
    public val result: String,
    /** Line-level delta (`-` golden-only, `+` actual-only); empty on match. Capped, may end with an overflow marker. */
    public val delta: List<String>,
)

/**
 * Compares two canonical semantics forms ([SemanticsDump.toCanonicalJson]) by string equality and,
 * on mismatch, emits a bounded line diff. A structured per-node differ is intentionally deferred:
 * the gate is equality, and the line diff is a readable-enough signal. Lines present in both forms
 * are elided; a changed value line surfaces as a `-`/`+` pair.
 */
public class SemanticsDiffer(private val maxDeltaLines: Int = 40) {
    /** Compares [golden] vs [actual] canonical strings. */
    public fun compare(golden: String, actual: String): SemanticsDiffResult {
        if (golden == actual) return SemanticsDiffResult("match", emptyList())
        val g = golden.lines()
        val a = actual.lines()
        val gSet = g.toSet()
        val aSet = a.toSet()
        val lines = g.filter { it !in aSet }.map { "- $it" } + a.filter { it !in gSet }.map { "+ $it" }
        val capped = if (lines.size > maxDeltaLines) {
            lines.take(maxDeltaLines) + "… (${lines.size - maxDeltaLines} more)"
        } else {
            lines
        }
        return SemanticsDiffResult("mismatch", capped)
    }
}
