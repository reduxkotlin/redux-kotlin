package org.reduxkotlin.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SemanticsTest {
    private fun leaf(text: String) = SemanticsDump.Node(
        role = null, text = listOf(text), contentDescription = emptyList(),
        testTag = null, enabled = null, selected = null, toggle = null, children = emptyList(),
    )

    private val sample = SemanticsDump(
        roots = listOf(
            SemanticsDump.Node(
                role = "button", text = listOf("Save"), contentDescription = emptyList(),
                testTag = "save", enabled = false, selected = null, toggle = null,
                children = listOf(leaf("Icon")),
            ),
        ),
        texts = listOf("Save", "Icon"),
    )

    @Test fun canonical_json_is_deterministic() {
        assertEquals(sample.toCanonicalJson(), sample.toCanonicalJson())
    }

    @Test fun canonical_json_has_no_bounds_key() {
        assertTrue("bounds" !in sample.toCanonicalJson())
    }

    @Test fun text_form_shows_fields_and_indents_children() {
        val t = sample.toText()
        assertTrue("role=button" in t, t)
        assertTrue("Save" in t, t)
        assertTrue("testTag=save" in t, t)
        assertTrue("enabled=false" in t, t)
        assertTrue("  " in t, "child should be indented") // 2-space indent
    }

    @Test fun empty_dump_text_and_json() {
        assertEquals("(no semantics)", SemanticsDump.EMPTY.toText())
        assertEquals("[]", SemanticsDump.EMPTY.toCanonicalJson().trim())
        assertTrue(SemanticsDump.EMPTY.texts.isEmpty())
    }
}
