package org.reduxkotlin.snapshot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A batch input manifest: shared [defaults] plus a list of independent [shots]. */
@Serializable
internal data class BatchManifest(
    val defaults: ManifestDefaults = ManifestDefaults(),
    val shots: List<ShotSpec> = emptyList(),
)

/** Manifest-level defaults applied to each shot unless the shot overrides them. */
@Serializable
internal data class ManifestDefaults(
    val theme: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val density: Float? = null,
)

/** One shot in a batch. Exactly one of [preset]/[stateJson] must be set. */
@Serializable
internal data class ShotSpec(
    val id: String,
    val scene: String,
    val preset: String? = null,
    val stateJson: JsonElement? = null,
    val theme: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val density: Float? = null,
    val out: String? = null,
)
