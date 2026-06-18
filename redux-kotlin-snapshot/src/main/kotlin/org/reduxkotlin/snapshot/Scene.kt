package org.reduxkotlin.snapshot

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonElement

/**
 * The state a scene renders from. The app decodes [Json] itself; the library never deserializes
 * app types.
 */
public sealed interface SnapshotInput {
    /** A named, scene-defined preset. */
    public data class Preset(
        /** The preset name. */
        public val name: String,
    ) : SnapshotInput

    /** Arbitrary JSON the scene's render block decodes itself. */
    public data class Json(
        /** The raw JSON element. */
        public val json: JsonElement,
    ) : SnapshotInput
}

/** What a scene's render block receives: the [input] to render and the resolved [theme]. */
public class SceneArgs(
    /** The state/input to render. */
    public val input: SnapshotInput,
    /** The resolved theme name, or null. */
    public val theme: String?,
)

/** Per-scene default render dimensions/theme; null fields fall back to the app defaults. */
public class SceneDefaults(
    /** Default width in dp, or null to inherit. */
    public var width: Int? = null,
    /** Default height in dp, or null to inherit. */
    public var height: Int? = null,
    /** Default density, or null to inherit. */
    public var density: Float? = null,
    /** Default theme name, or null to inherit. */
    public var theme: String? = null,
)

/** A registered fixture: a [name], declared [presets], defaults, and how to produce a composable. */
public class Scene internal constructor(
    /** The scene's unique name. */
    public val name: String,
    /** Declared preset names (metadata for discovery/validation). */
    public val presets: List<String>,
    internal val defaults: SceneDefaults,
    internal val render: (SceneArgs) -> @Composable () -> Unit,
)
