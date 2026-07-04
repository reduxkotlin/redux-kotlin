package org.reduxkotlin.snapshot

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SemanticsExtractionTest {
    private val richContent: @Composable () -> Unit = {
        Column {
            Text("Title")
            Button(onClick = {}, enabled = false, modifier = Modifier.testTag("save")) { Text("Save") }
            Text("Logo", modifier = Modifier.semantics { contentDescription = "logo image" })
        }
    }

    private fun render() = ImageComposeSceneBackend().render(
        RenderSpec(widthDp = 200, heightDp = 200, density = 2f, content = richContent),
    ).semantics

    @Test fun captures_text_in_traversal_order() {
        val texts = render().texts
        assertTrue("Title" in texts, texts.toString())
        assertTrue("Save" in texts, texts.toString())
    }

    @Test fun button_node_carries_role_testTag_and_disabled() {
        val dump = render()
        val button = allNodes(dump).first { it.testTag == "save" }
        assertEquals("button", button.role)
        assertEquals(false, button.enabled)
        assertTrue("Save" in button.text, button.text.toString())
    }

    @Test fun merged_tree_absorbs_the_buttons_text_child() {
        val dump = render()
        val button = allNodes(dump).first { it.testTag == "save" }
        // The child Text("Save") is merged into the button; it is not a separate child node.
        assertTrue(button.children.none { it.text == listOf("Save") }, "Text child should be absorbed")
    }

    @Test fun content_description_is_captured() {
        assertTrue(allNodes(render()).any { it.contentDescription == listOf("logo image") })
    }

    @Test fun same_input_yields_identical_canonical_json() {
        assertEquals(render().toCanonicalJson(), render().toCanonicalJson())
    }

    @Test fun text_free_scene_yields_empty_texts() {
        // The 'counter' demo scene draws only bars, no semantics text.
        val dump = ImageComposeSceneBackend().render(
            RenderSpec(200, 200, 2f) {},
        ).semantics
        assertTrue(dump.texts.isEmpty())
    }

    private fun allNodes(dump: SemanticsDump): List<SemanticsDump.Node> {
        val out = mutableListOf<SemanticsDump.Node>()
        fun walk(n: SemanticsDump.Node) {
            out += n
            n.children.forEach(::walk)
        }
        dump.roots.forEach(::walk)
        return out
    }
}
