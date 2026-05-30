package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.reduxkotlin.sample.taskflow.model.Attachment
import org.reduxkotlin.sample.taskflow.ui.image.rememberBundledCardBytes
import org.reduxkotlin.sample.taskflow.ui.theme.Dimens

/**
 * Renders one card [Attachment] (spec-data.js -> `AttachmentChip`) as a Medium (12 dp) chip:
 *
 * - [Attachment.Image] — a fixed-size Coil [AsyncImage] thumbnail (Level 1) using the image's
 *   `alt` as its `contentDescription`. A shimmer placeholder shows while loading; on error it falls
 *   back first to a **bundled offline placeholder** PNG (from `composeResources`, chosen
 *   deterministically from the image url), and only to the broken-link glyph if that load also fails
 *   (Rule F: plain [AsyncImage], never `SubcomposeAsyncImage`).
 * - [Attachment.Link] — a preview card: the link title (or the host parsed from its URL), the
 *   host as a Label Small subtitle, and an optional `imageUrl` thumbnail.
 *
 * When [onRemove] is non-null (edit mode) a remove icon button is shown.
 *
 * Pure presentational (Rule C): immutable [attachment] data + a remembered, nullable
 * [onRemove] callback. Reads no store, runs no logic.
 *
 * @param attachment the attachment to render.
 * @param onRemove invoked when the remove button is tapped; `null` hides it (view mode).
 * @param modifier the [Modifier] for this chip.
 */
@Composable
public fun AttachmentChip(attachment: Attachment, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (attachment) {
                    is Attachment.Image -> ImageAttachment(attachment)
                    is Attachment.Link -> LinkAttachment(attachment)
                }
            }
            if (onRemove != null) {
                RemoveButton(onRemove = onRemove)
            }
        }
    }
}

/**
 * Image variant: a Coil thumbnail (Level 1) over a shimmer. On remote error it tries a bundled
 * offline placeholder (chosen from the image url); only if that also fails does the broken-link glyph
 * show.
 */
@Composable
private fun ImageAttachment(image: Attachment.Image) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(THUMBNAIL_HEIGHT),
    ) {
        Box(contentAlignment = Alignment.Center) {
            var state by remember(image.url) { mutableStateOf(ImageState.LOADING) }
            var fallbackFailed by remember(image.url) { mutableStateOf(false) }

            if (state == ImageState.LOADING) {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }
            AsyncImage(
                model = image.url,
                contentDescription = image.alt,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                onSuccess = { state = ImageState.LOADED },
                onError = { state = ImageState.ERROR },
            )
            if (state == ImageState.ERROR) {
                val bundled = rememberBundledCardBytes(image.url)
                when {
                    bundled != null && !fallbackFailed -> AsyncImage(
                        model = bundled,
                        contentDescription = image.alt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                        onError = { fallbackFailed = true },
                    )

                    else -> BrokenLinkGlyph()
                }
            }
        }
    }
}

/** Link variant: a preview card with title (or host), host subtitle, and optional thumbnail. */
@Composable
private fun LinkAttachment(link: Attachment.Link) {
    val host = remember(link.url) { hostOf(link.url) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        if (link.imageUrl != null) {
            AsyncImage(
                model = link.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(LINK_THUMB_SIZE)
                    .clip(MaterialTheme.shapes.small),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title ?: host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = host,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The edit-mode remove button. */
@Composable
private fun RemoveButton(onRemove: () -> Unit) {
    IconButton(
        onClick = onRemove,
        modifier = Modifier.semantics { contentDescription = "Remove attachment" },
    ) {
        Text("✕", style = MaterialTheme.typography.titleMedium)
    }
}

/** A broken-link glyph shown when an image attachment fails to load. */
@Composable
private fun BrokenLinkGlyph() {
    Text(
        text = "⚠",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** An animated shimmer rectangle used as the image loading placeholder. */
@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "attachmentShimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = SHIMMER_MIN_ALPHA,
        targetValue = SHIMMER_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "attachmentShimmerAlpha",
    )
    Box(
        modifier = modifier
            .alpha(shimmerAlpha)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** The internal image load state used to switch shimmer / error / content. */
private enum class ImageState { LOADING, LOADED, ERROR }

/**
 * Parses the host (authority) from [url] without `java.net.URI` (KMP-safe): strips the scheme,
 * then takes the segment up to the first `/`, `?`, or `#`, dropping any `user@` prefix.
 */
private fun hostOf(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
    val host = authority.substringAfterLast('@')
    return host.ifBlank { url }
}

private val THUMBNAIL_HEIGHT = 96.dp
private val LINK_THUMB_SIZE = 48.dp
private const val SHIMMER_MIN_ALPHA = 0.3f
private const val SHIMMER_MAX_ALPHA = 0.9f
private const val SHIMMER_PERIOD_MS = 900
