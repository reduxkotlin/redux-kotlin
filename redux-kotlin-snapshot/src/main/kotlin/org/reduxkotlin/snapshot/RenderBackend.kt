@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.reduxkotlin.snapshot

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat

/** The output of a render: PNG bytes plus a [SemanticsDump]. */
public class RenderResult(
    /** Encoded PNG bytes. */
    public val png: ByteArray,
    /** Semantic content captured from the scene. */
    public val semantics: SemanticsDump = SemanticsDump.EMPTY,
)

/** Rasterizes a [RenderSpec] to a [RenderResult]. The only Skiko-touching surface. */
internal interface RenderBackend {
    fun render(spec: RenderSpec): RenderResult
}

/** Headless Compose render via [ImageComposeScene] (JVM/Skiko). */
internal class ImageComposeSceneBackend : RenderBackend {
    override fun render(spec: RenderSpec): RenderResult {
        val scene = ImageComposeScene(
            width = spec.widthPx,
            height = spec.heightPx,
            density = Density(spec.density),
        ) { spec.content() }
        return try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
            RenderResult(png, extractSemantics(scene))
        } finally {
            scene.close()
        }
    }

    // Reading the experimental semantics tree must never fail a render: pixels are the primary
    // artifact. Any failure degrades to SemanticsDump.EMPTY while keeping the PNG.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractSemantics(scene: ImageComposeScene): SemanticsDump = try {
        val roots = scene.semanticsOwners
            .sortedBy { it.rootSemanticsNode.id }
            .map { toNode(it.rootSemanticsNode) }
        if (roots.isEmpty()) {
            SemanticsDump.EMPTY
        } else {
            val texts = mutableListOf<String>()
            roots.forEach { collectTexts(it, texts) }
            SemanticsDump(roots, texts)
        }
    } catch (e: Exception) {
        SemanticsDump.EMPTY
    }

    private fun toNode(n: SemanticsNode): SemanticsDump.Node {
        val cfg = n.config
        return SemanticsDump.Node(
            role = cfg.getOrNull(SemanticsProperties.Role)?.toString()?.lowercase(),
            text = cfg.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList(),
            contentDescription = cfg.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList(),
            testTag = cfg.getOrNull(SemanticsProperties.TestTag),
            enabled = if (cfg.contains(SemanticsProperties.Disabled)) false else null,
            selected = cfg.getOrNull(SemanticsProperties.Selected),
            toggle = cfg.getOrNull(SemanticsProperties.ToggleableState)?.toString(),
            children = n.children.map { toNode(it) },
        )
    }

    private fun collectTexts(n: SemanticsDump.Node, out: MutableList<String>) {
        out += n.text
        n.children.forEach { collectTexts(it, out) }
    }

    // Public SemanticsConfiguration API is get(key) + contains(key); this pairs them safely.
    private fun <T> SemanticsConfiguration.getOrNull(key: SemanticsPropertyKey<T>): T? =
        if (contains(key)) get(key) else null
}
