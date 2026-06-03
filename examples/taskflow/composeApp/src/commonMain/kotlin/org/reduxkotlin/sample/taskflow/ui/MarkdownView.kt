package org.reduxkotlin.sample.taskflow.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Read-only rendered markdown for a card description (spec-data.js -> `MarkdownView`):
 * headings, lists, inline code, links, and images. Built on
 * [com.mikepenz.markdown.m3.Markdown] (the multiplatform-markdown-renderer-m3 0.41.0 M3
 * binding) with the Coil3 [Coil3ImageTransformerImpl] so markdown `![alt](url)` images
 * reuse the app-wide Coil loader.
 *
 * Colors follow the spec: `onSurface` body text, `primary` underlined links, and a
 * `surfaceVariant` code background. Body text is Body Large; code is monospace.
 *
 * Pure presentational (Rule C): it takes only the immutable [markdown] string, reads no
 * store, and runs no business logic. An empty / blank string renders nothing.
 *
 * @param markdown the markdown source to render.
 * @param modifier the [Modifier] for this view.
 */
@Composable
public fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    if (markdown.isBlank()) return

    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Markdown(
        content = markdown,
        colors = markdownColor(
            text = scheme.onSurface,
            codeBackground = scheme.surfaceVariant,
            inlineCodeBackground = scheme.surfaceVariant,
            dividerColor = scheme.outlineVariant,
        ),
        typography = markdownTypography(
            text = typography.bodyLarge,
            paragraph = typography.bodyLarge,
            textLink = TextLinkStyles(
                style = TextStyle(
                    color = scheme.primary,
                    textDecoration = TextDecoration.Underline,
                ).toSpanStyle(),
            ),
        ),
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = modifier,
    )
}
