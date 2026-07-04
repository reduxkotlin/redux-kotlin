package org.reduxkotlin.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SemanticsDifferTest {
    private val differ = SemanticsDiffer()

    @Test fun equal_strings_match_with_no_delta() {
        val r = differ.compare("a\nb\nc", "a\nb\nc")
        assertEquals("match", r.result)
        assertTrue(r.delta.isEmpty())
    }

    @Test fun changed_line_shows_removed_and_added() {
        val r = differ.compare("""  "text": ["Save"]""", """  "text": ["Saved"]""")
        assertEquals("mismatch", r.result)
        assertTrue(r.delta.any { it.startsWith("-") && "Save" in it }, r.delta.toString())
        assertTrue(r.delta.any { it.startsWith("+") && "Saved" in it }, r.delta.toString())
    }

    @Test fun delta_is_capped_with_overflow_marker() {
        val golden = (1..100).joinToString("\n") { "g$it" }
        val actual = (1..100).joinToString("\n") { "a$it" }
        val r = SemanticsDiffer(maxDeltaLines = 10).compare(golden, actual)
        assertEquals("mismatch", r.result)
        assertEquals(11, r.delta.size) // 10 + overflow marker
        assertTrue(r.delta.last().startsWith("…"), r.delta.last())
    }
}
