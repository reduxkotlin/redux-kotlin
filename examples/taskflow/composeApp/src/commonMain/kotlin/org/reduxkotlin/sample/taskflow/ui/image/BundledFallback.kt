package org.reduxkotlin.sample.taskflow.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.reduxkotlin.composeapp.generated.resources.Res

/**
 * The count of bundled placeholder avatars shipped under `composeResources/files/avatars/`
 * (`avatar-0.png` .. `avatar-5.png`). Used to map a seed id onto a deterministic slot.
 */
private const val AVATAR_FALLBACK_COUNT = 6

/**
 * The count of bundled placeholder card/attachment images shipped under
 * `composeResources/files/cards/` (`card-0.png` .. `card-2.png`).
 */
private const val CARD_FALLBACK_COUNT = 3

/**
 * Deterministically maps an arbitrary [id] string onto `0 until [count]` via its hash, so the same
 * id always resolves to the same bundled-image slot (stable across recompositions and launches).
 */
private fun slotFor(id: String, count: Int): Int = (id.hashCode() and Int.MAX_VALUE) % count

/**
 * Loads the bytes of a bundled offline-fallback **avatar** image chosen deterministically from
 * [seedId], as Compose-resources state. Returns `null` until the (suspended) read completes or if
 * the read fails — callers should fall through to their ultimate fallback (the monogram) when it is
 * `null`. The bytes feed a plain `coil3.compose.AsyncImage(model = bytes)` (Coil's `ByteArray`
 * source), so no platform-specific decoding is needed.
 *
 * @param seedId the stable account id; hashed to pick a bundled slot.
 */
@Composable
public fun rememberBundledAvatarBytes(seedId: String): ByteArray? {
    val path = remember(seedId) { "files/avatars/avatar-${slotFor(seedId, AVATAR_FALLBACK_COUNT)}.png" }
    return rememberResourceBytes(path)
}

/**
 * Loads the bytes of a bundled offline-fallback **card / attachment** image chosen deterministically
 * from [seedId], as Compose-resources state. Returns `null` until the read completes or if it fails,
 * so callers fall through to their ultimate fallback (the broken-link glyph). Feeds a plain
 * `coil3.compose.AsyncImage(model = bytes)`.
 *
 * @param seedId a stable id for the attachment (e.g. its url); hashed to pick a bundled slot.
 */
@Composable
public fun rememberBundledCardBytes(seedId: String): ByteArray? {
    val path = remember(seedId) { "files/cards/card-${slotFor(seedId, CARD_FALLBACK_COUNT)}.png" }
    return rememberResourceBytes(path)
}

/**
 * Reads a Compose-resources file [path] into a remembered [ByteArray] state, re-reading whenever the
 * [path] changes. The read runs in a [LaunchedEffect] (it is suspending); any failure leaves the
 * state `null` so the caller can degrade to its own ultimate fallback.
 */
@Composable
private fun rememberResourceBytes(path: String): ByteArray? {
    var bytes by remember(path) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(path) {
        bytes = runCatching { Res.readBytes(path) }.getOrNull()
    }
    return bytes
}
