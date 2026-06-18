@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.reduxkotlin.snapshot

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat

/**
 * A deterministic dump of a rendered scene's content, beside the pixels. In this slice it carries
 * only the rendered text strings; the node/role tree is added in a later plan. Lets a consumer
 * assert content without relying on vision over the PNG.
 */
public class SemanticsDump(
    /** Rendered text strings, in traversal order. */
    public val texts: List<String>,
) {
    /** Shared instances. */
    public companion object {
        /** An empty dump (no semantics captured). */
        public val EMPTY: SemanticsDump = SemanticsDump(emptyList())
    }
}

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
            RenderResult(png, SemanticsDump.EMPTY)
        } finally {
            scene.close()
        }
    }
}
