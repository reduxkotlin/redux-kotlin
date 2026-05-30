package org.reduxkotlin.sample.taskflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.reduxkotlin.sample.taskflow.ui.image.rememberBundledAvatarBytes
import org.reduxkotlin.sample.taskflow.ui.theme.LocalSemanticColors

/**
 * A circular account avatar with a three-tier fallback chain (Rule F: plain [AsyncImage], never
 * `SubcomposeAsyncImage`):
 *
 * 1. the remote [avatarUrl] (Coil async image);
 * 2. on remote error/empty, a **bundled offline placeholder** PNG (shipped in `composeResources`,
 *    chosen deterministically from [seedId] via [rememberBundledAvatarBytes]) drawn as an
 *    `AsyncImage(model = bytes)`;
 * 3. if even the bundled load fails, the deterministic colored **monogram** (initials of [name] on a
 *    tonal background hashed from [seedId]) — always present underneath, and the loading state too.
 *
 * Pure presentational: it takes only immutable data + primitives and reads no store.
 *
 * @param name display name; drives the monogram initials and the accessibility label.
 * @param avatarUrl remote image URL, or `null` to render the monogram directly.
 * @param seedId stable account id; hashed to pick a deterministic monogram background.
 * @param size avatar diameter (xs 24 · sm 30 · md 34 · lg 56 per the spec).
 * @param presenceOnline `true` shows an online (green) presence dot, `false` an offline
 *  (outline) dot, `null` shows no dot.
 * @param modifier the [Modifier] for this avatar.
 */
@Composable
public fun Avatar(
    name: String,
    avatarUrl: String?,
    seedId: String,
    size: Dp = 34.dp,
    presenceOnline: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    val (monogramBg, monogramOn) = monogramColors(seedId)
    val initials = remember(name) { monogramInitials(name) }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        // Monogram fallback — always present underneath; the image (when it loads) covers it.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(monogramBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = monogramOn,
                textAlign = TextAlign.Center,
            )
        }

        AvatarImage(name = name, avatarUrl = avatarUrl, seedId = seedId, size = size)

        if (presenceOnline != null) {
            PresenceDot(
                online = presenceOnline,
                size = size,
                onlineColor = semantic.online,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * The avatar image tiers, drawn over the monogram (Rule F: plain [AsyncImage]): the remote
 * [avatarUrl] first, then — only once the remote has errored — a bundled offline placeholder picked
 * deterministically from [seedId]. When both fail the monogram beneath shows through.
 */
@Composable
private fun AvatarImage(name: String, avatarUrl: String?, seedId: String, size: Dp) {
    var remoteFailed by remember(avatarUrl) { mutableStateOf(avatarUrl == null) }
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = name,
            modifier = Modifier.size(size).clip(CircleShape),
            onError = { remoteFailed = true },
        )
    }
    if (remoteFailed) {
        val bundled = rememberBundledAvatarBytes(seedId)
        if (bundled != null) {
            AsyncImage(
                model = bundled,
                contentDescription = name,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

/**
 * The corner presence dot: a small filled circle on a [surface][MaterialTheme.colorScheme.surface]
 * ring, [onlineColor] when [online], otherwise the theme outline. Sized relative to the avatar [size].
 */
@Composable
private fun PresenceDot(online: Boolean, size: Dp, onlineColor: Color, modifier: Modifier = Modifier) {
    val dot = (size / 4).coerceAtLeast(8.dp)
    Box(
        modifier = modifier
            .size(dot)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dot - 2.dp)
                .clip(CircleShape)
                .background(if (online) onlineColor else MaterialTheme.colorScheme.outline),
        )
    }
}

/** Up-to-two-letter initials from a display [name] (first letter of the first two words). */
private fun monogramInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * A deterministic (container, on-container) monogram color pair from a [seedId], cycling the
 * seed's tonal palette (primary / tertiary / secondary) so the same id always renders the same
 * color with a readable text/background contrast.
 */
@Composable
private fun monogramColors(seedId: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
    )
    val bucket = (seedId.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[bucket]
}
