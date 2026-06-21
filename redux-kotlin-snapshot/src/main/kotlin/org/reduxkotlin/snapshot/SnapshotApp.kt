package org.reduxkotlin.snapshot

import androidx.compose.runtime.Composable

/** Thrown for usage errors (unknown scene/preset, bad input). Caught at the CLI/test boundary. */
public class SnapshotException(message: String) : RuntimeException(message)

/** A resolved request ready to render: the [scene], its [input], and the resolved dims/theme. */
public class ResolvedShot(
    /** The resolved scene. */
    public val scene: Scene,
    /** The input to render. */
    public val input: SnapshotInput,
    /** Resolved width in dp. */
    public val widthDp: Int,
    /** Resolved height in dp. */
    public val heightDp: Int,
    /** Resolved density. */
    public val density: Float,
    /** Resolved theme name, or null. */
    public val theme: String?,
)

/** Global render defaults applied to every scene unless overridden. */
public class GlobalDefaults(
    /** Default width in dp. */
    public var width: Int = 411,
    /** Default height in dp. */
    public var height: Int = 891,
    /** Default density. */
    public var density: Float = 2f,
    /** Default theme name, or null. */
    public var theme: String? = "dark",
)

/** The scene registry produced by [snapshotApp]. */
public class SnapshotApp internal constructor(
    /** All registered scenes. */
    public val scenes: List<Scene>,
    internal val defaults: GlobalDefaults,
) {
    private val byName = scenes.associateBy { it.name }
    private val defaultBackend: RenderBackend = ImageComposeSceneBackend()

    /** Resolves (scene, input, dims) applying global, then scene, then per-call overrides. */
    public fun resolve(
        scene: String,
        input: SnapshotInput,
        theme: String?,
        width: Int?,
        height: Int?,
        density: Float?,
    ): ResolvedShot {
        val s = byName[scene] ?: throw SnapshotException(
            "unknown scene '$scene' (have: ${scenes.joinToString { it.name }})",
        )
        return ResolvedShot(
            scene = s,
            input = input,
            widthDp = width ?: s.defaults.width ?: defaults.width,
            heightDp = height ?: s.defaults.height ?: defaults.height,
            density = density ?: s.defaults.density ?: defaults.density,
            theme = theme ?: s.defaults.theme ?: defaults.theme,
        )
    }

    /**
     * Renders [scene] from [input] to a [RenderResult] (PNG + semantics) — the headless
     * `f(state) -> UI` primitive. Applies global/scene/per-call defaults.
     */
    public fun render(
        scene: String,
        input: SnapshotInput,
        theme: String? = null,
        width: Int? = null,
        height: Int? = null,
        density: Float? = null,
    ): RenderResult = renderResult(resolve(scene, input, theme, width, height, density), defaultBackend)

    /** Renders a resolved shot via [backend], returning PNG + semantics. */
    internal fun renderResult(shot: ResolvedShot, backend: RenderBackend): RenderResult {
        val composable = shot.scene.render(SceneArgs(shot.input, shot.theme))
        return backend.render(RenderSpec(shot.widthDp, shot.heightDp, shot.density, composable))
    }
}

/** Builder for [snapshotApp]. */
public class SnapshotAppBuilder internal constructor() {
    private val defaults = GlobalDefaults()
    private val scenes = mutableListOf<Scene>()

    /** Set global defaults. */
    public fun defaults(block: GlobalDefaults.() -> Unit) {
        defaults.block()
    }

    /** Register a scene named [name]. */
    public fun scene(name: String, block: SceneBuilder.() -> Unit) {
        val b = SceneBuilder().apply(block)
        scenes += Scene(
            name = name,
            presets = b.presetList.toList(),
            defaults = b.sceneDefaults,
            render = b.renderBlock ?: throw SnapshotException("scene '$name' has no render { } block"),
        )
    }

    internal fun build(): SnapshotApp = SnapshotApp(scenes.toList(), defaults)
}

/** Per-scene DSL builder. */
public class SceneBuilder internal constructor() {
    internal val sceneDefaults = SceneDefaults()
    internal val presetList = mutableListOf<String>()
    internal var renderBlock: ((SceneArgs) -> @Composable () -> Unit)? = null

    /** Declare preset names (metadata for `--list`/validation). */
    public fun presets(vararg names: String) {
        presetList += names
    }

    /** Set per-scene default dims/theme. */
    public fun defaults(block: SceneDefaults.() -> Unit) {
        sceneDefaults.block()
    }

    /** Produce the composable for the given input. */
    public fun render(block: (SceneArgs) -> @Composable () -> Unit) {
        renderBlock = block
    }
}

/** Entry point: build a scene registry. */
public fun snapshotApp(block: SnapshotAppBuilder.() -> Unit): SnapshotApp = SnapshotAppBuilder().apply(block).build()
