# redux-kotlin-snapshot (Plan 1 — core) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the `redux-kotlin-snapshot` JVM library with a headless Compose render engine, a scene-registry DSL, golden-image diffing, and a single-shot CLI — proving `f(state) → UI` end to end against a built-in demo scene.

**Architecture:** A `kotlin("jvm")` + Compose module. An internal `RenderBackend` rasterizes a `@Composable` to PNG via `ImageComposeScene`/Skiko (proven code lifted from the TaskFlow render spike). A `snapshotApp { scene(...) }` DSL registers app fixtures (`SceneArgs → @Composable`); the library never deserializes app types. A `Differ` compares against committed goldens with per-pixel tolerance. `runCli` exposes single-shot render + verify. A built-in demo scene (no redux) lets the module self-test the full pipe in CI.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.11.1 (`compose.desktop.currentOs`/Skiko), kotlinx.serialization (JSON), Clikt 4.4.0, kotlin-test. Scope of THIS plan excludes: batch/report.json, semantic dump, HTML dashboard, action-replay/filmstrip, Roborazzi/Android backend, external-consumer wiring (Plans 2–3).

Spec: `docs/superpowers/specs/2026-06-16-redux-kotlin-snapshot-design.md`.

---

## Reference: known-good engine code (from the spike)

The render primitive already works in the spike — use it verbatim as the basis for `ImageComposeSceneBackend`:
```kotlin
// examples/taskflow/.../render/RenderScenes.kt (spike)
internal fun renderToPng(width: Int, height: Int, content: @Composable () -> Unit): ByteArray {
    val scene = ImageComposeScene(width = width, height = height, density = Density(2f)) { content() }
    return try { scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes } finally { scene.close() }
}
```

## File structure (created by this plan)

```
redux-kotlin-snapshot/
  build.gradle.kts
  src/main/kotlin/org/reduxkotlin/snapshot/
    RenderSpec.kt            # RenderSpec data class + px math
    RenderBackend.kt         # interface + ImageComposeSceneBackend
    Scene.kt                 # Scene, SceneArgs, SnapshotInput
    SnapshotApp.kt           # snapshotApp { } DSL + registry + resolve
    Differ.kt                # per-pixel diff: DiffResult + compare()
    GoldenStore.kt           # read/write golden PNGs
    DemoScene.kt             # built-in no-redux demo scene (self-test)
    cli/Main.kt              # main() -> runCli
    cli/Cli.kt               # Clikt commands: single render + --verify + --list
  src/test/kotlin/org/reduxkotlin/snapshot/
    RenderSpecTest.kt
    RenderBackendTest.kt
    DifferTest.kt
    SnapshotAppTest.kt
    CliTest.kt
    DemoGoldenTest.kt
  src/test/resources/snapshots/demo-default.png   # committed golden (generated in Task 9)
```

Also modified: `settings.gradle.kts` (include module), `redux-kotlin-bom/build.gradle.kts` (constraint), `gradle/libs.versions.toml` (clikt alias already exists; reuse).

---

### Task 1: Scaffold the module + build

**Files:**
- Create: `redux-kotlin-snapshot/build.gradle.kts`
- Modify: `settings.gradle.kts` (add include)
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/.gitkeep`

- [ ] **Step 1: Add the module to settings**

Modify `settings.gradle.kts` — add `":redux-kotlin-snapshot",` to the `include(` list, right after `":redux-kotlin-devtools-cli",`.

- [ ] **Step 2: Write the build file**

Create `redux-kotlin-snapshot/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.common")
    kotlin("jvm")
    application
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Pin the compile JDK to 17 — matches the repo convention (JvmTarget.JVM_17) and the
// redux-kotlin-devtools-cli fix (Compose 1.11.x + Skiko load fine at 17). Do NOT raise to 21.
// (The devtools-cli UnsupportedClassVersionError was a 21-compiled DEP on a 17 test JVM; the fix
// was making everything 17, not raising the toolchain.)
kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "rk-snapshot"
    mainClass.set("org.reduxkotlin.snapshot.cli.MainKt")
}

dependencies {
    api(project(":redux-kotlin"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}
```

- [ ] **Step 3: Create the source package marker**

Create empty file `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/.gitkeep`.

- [ ] **Step 4: Verify the module configures and pick the toolchain level**

Run: `./gradlew :redux-kotlin-snapshot:dependencies --configuration runtimeClasspath -q | grep -i skiko`
Then inspect the skiko/compose jar bytecode level if the build later fails to load classes. Run:
`./gradlew :redux-kotlin-snapshot:help -q`
Expected: `BUILD SUCCESSFUL`. `17` matches the repo convention and is verified to work with Compose 1.11.x/Skiko (devtools-cli runs at 17).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts redux-kotlin-snapshot/build.gradle.kts redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/.gitkeep
git commit -m "build(snapshot): scaffold redux-kotlin-snapshot module"
```

---

### Task 2: RenderSpec + px math

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderSpec.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/RenderSpecTest.kt`

- [ ] **Step 1: Write the failing test**

Create `RenderSpecTest.kt`:
```kotlin
package org.reduxkotlin.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals

internal class RenderSpecTest {
    @Test fun px_is_dp_times_density_rounded() {
        val spec = RenderSpec(widthDp = 411, heightDp = 891, density = 2f) {}
        assertEquals(822, spec.widthPx)
        assertEquals(1782, spec.heightPx)
    }

    @Test fun density_rounds_half_up() {
        val spec = RenderSpec(widthDp = 10, heightDp = 10, density = 1.75f) {}
        assertEquals(18, spec.widthPx) // 17.5 -> 18
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*RenderSpecTest*"`
Expected: FAIL — `RenderSpec` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `RenderSpec.kt`:
```kotlin
package org.reduxkotlin.snapshot

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

/** A fully-resolved render request: logical [widthDp]x[heightDp] at [density] (output px = dp*density). */
public class RenderSpec(
    public val widthDp: Int,
    public val heightDp: Int,
    public val density: Float,
    public val content: @Composable () -> Unit,
) {
    /** Output pixel width. */
    public val widthPx: Int get() = (widthDp * density).roundToInt()
    /** Output pixel height. */
    public val heightPx: Int get() = (heightDp * density).roundToInt()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*RenderSpecTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderSpec.kt redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/RenderSpecTest.kt
git commit -m "feat(snapshot): RenderSpec with dp->px math"
```

---

### Task 3: RenderBackend + ImageComposeSceneBackend

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderBackend.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/RenderBackendTest.kt`

- [ ] **Step 1: Write the failing test**

Create `RenderBackendTest.kt`:
```kotlin
package org.reduxkotlin.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RenderBackendTest {
    @Test fun renders_a_red_box_to_a_png_of_the_right_size() {
        val png = ImageComposeSceneBackend().render(
            RenderSpec(widthDp = 100, heightDp = 50, density = 2f) {
                Box(Modifier.fillMaxSize().background(Color.Red))
            },
        )
        assertTrue(png.isNotEmpty(), "png bytes empty")
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(200, img.width)
        assertEquals(100, img.height)
        // center pixel is red (0xFFFF0000)
        val argb = img.getRGB(100, 50)
        assertEquals(0xFF0000, argb and 0xFFFFFF)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*RenderBackendTest*"`
Expected: FAIL — `ImageComposeSceneBackend` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `RenderBackend.kt` (lifts the spike's proven `renderToPng`):
```kotlin
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.reduxkotlin.snapshot

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat

/** Rasterizes a [RenderSpec] to PNG bytes. The only Skiko-touching surface. */
internal interface RenderBackend {
    fun render(spec: RenderSpec): ByteArray
}

/** Headless Compose render via [ImageComposeScene] (JVM/Skiko). */
internal class ImageComposeSceneBackend : RenderBackend {
    override fun render(spec: RenderSpec): ByteArray {
        val scene = ImageComposeScene(
            width = spec.widthPx,
            height = spec.heightPx,
            density = Density(spec.density),
        ) { spec.content() }
        return try {
            scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encode returned null")
        } finally {
            scene.close()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*RenderBackendTest*"`
Expected: PASS at `jvmToolchain(17)`.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/RenderBackend.kt redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/RenderBackendTest.kt
git commit -m "feat(snapshot): ImageComposeScene render backend (PNG)"
```

---

### Task 4: Scene, SceneArgs, SnapshotInput

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Scene.kt`

- [ ] **Step 1: Write the types (no test — exercised via SnapshotApp in Task 5)**

Create `Scene.kt`:
```kotlin
package org.reduxkotlin.snapshot

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonElement

/** The state a scene renders from. The app decodes [Json]; the library never deserializes app types. */
public sealed interface SnapshotInput {
    /** A named, scene-defined preset. */
    public data class Preset(public val name: String) : SnapshotInput
    /** Arbitrary JSON the scene's render block decodes itself. */
    public data class Json(public val json: JsonElement) : SnapshotInput
}

/** What a scene's render block receives. */
public class SceneArgs(public val input: SnapshotInput, public val theme: String?)

/** Per-scene default render dimensions/theme; null fields fall back to the app defaults. */
public class SceneDefaults(
    public var width: Int? = null,
    public var height: Int? = null,
    public var density: Float? = null,
    public var theme: String? = null,
)

/** A registered fixture: a name + declared presets + how to produce a composable from input. */
public class Scene internal constructor(
    public val name: String,
    public val presets: List<String>,
    internal val defaults: SceneDefaults,
    internal val render: (SceneArgs) -> @Composable () -> Unit,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :redux-kotlin-snapshot:compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Scene.kt
git commit -m "feat(snapshot): Scene/SceneArgs/SnapshotInput types"
```

---

### Task 5: snapshotApp DSL + registry + resolve

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotApp.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SnapshotAppTest.kt`

- [ ] **Step 1: Write the failing test**

Create `SnapshotAppTest.kt`:
```kotlin
package org.reduxkotlin.snapshot

import androidx.compose.foundation.layout.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class SnapshotAppTest {
    private val app = snapshotApp {
        defaults { width = 400; height = 800; density = 2f; theme = "dark" }
        scene("demo") { presets("default"); render { { Box(androidx.compose.ui.Modifier) } } }
    }

    @Test fun resolves_defaults_into_render_spec() {
        val r = app.resolve(scene = "demo", input = SnapshotInput.Preset("default"), theme = null,
            width = null, height = null, density = null)
        assertEquals(400, r.widthDp); assertEquals(800, r.heightDp); assertEquals(2f, r.density)
    }

    @Test fun per_call_overrides_win() {
        val r = app.resolve("demo", SnapshotInput.Preset("default"), theme = "light",
            width = 360, height = null, density = null)
        assertEquals(360, r.widthDp); assertEquals(800, r.heightDp)
    }

    @Test fun unknown_scene_fails() {
        assertFailsWith<SnapshotException> {
            app.resolve("nope", SnapshotInput.Preset("default"), null, null, null, null)
        }
    }

    @Test fun lists_scenes_and_presets() {
        assertEquals(listOf("demo"), app.scenes.map { it.name })
        assertEquals(listOf("default"), app.scenes.first().presets)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*SnapshotAppTest*"`
Expected: FAIL — `snapshotApp` / `SnapshotException` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `SnapshotApp.kt`:
```kotlin
package org.reduxkotlin.snapshot

import androidx.compose.runtime.Composable

/** Thrown for usage errors (unknown scene/preset, bad input). Caught at the CLI/test boundary. */
public class SnapshotException(message: String) : RuntimeException(message)

/** A resolved request ready to render. */
public class ResolvedShot(
    public val scene: Scene,
    public val input: SnapshotInput,
    public val widthDp: Int,
    public val heightDp: Int,
    public val density: Float,
    public val theme: String?,
)

/** Global render defaults. */
public class GlobalDefaults(
    public var width: Int = 411,
    public var height: Int = 891,
    public var density: Float = 2f,
    public var theme: String? = "dark",
)

/** The scene registry produced by [snapshotApp]. */
public class SnapshotApp internal constructor(
    public val scenes: List<Scene>,
    internal val defaults: GlobalDefaults,
) {
    private val byName = scenes.associateBy { it.name }

    /** Resolves (scene, input, dims) applying global ⊕ scene ⊕ per-call defaults. */
    public fun resolve(
        scene: String, input: SnapshotInput, theme: String?,
        width: Int?, height: Int?, density: Float?,
    ): ResolvedShot {
        val s = byName[scene] ?: throw SnapshotException(
            "unknown scene '$scene' (have: ${scenes.joinToString { it.name }})",
        )
        return ResolvedShot(
            scene = s, input = input,
            widthDp = width ?: s.defaults.width ?: defaults.width,
            heightDp = height ?: s.defaults.height ?: defaults.height,
            density = density ?: s.defaults.density ?: defaults.density,
            theme = theme ?: s.defaults.theme ?: defaults.theme,
        )
    }

    /** Renders a resolved shot to PNG via [backend]. */
    internal fun renderPng(shot: ResolvedShot, backend: RenderBackend): ByteArray {
        val composable = shot.scene.render(SceneArgs(shot.input, shot.theme))
        return backend.render(RenderSpec(shot.widthDp, shot.heightDp, shot.density, composable))
    }
}

/** DSL builder. */
public class SnapshotAppBuilder internal constructor() {
    private val defaults = GlobalDefaults()
    private val scenes = mutableListOf<Scene>()

    /** Set global defaults. */
    public fun defaults(block: GlobalDefaults.() -> Unit) { defaults.block() }

    /** Register a scene. */
    public fun scene(name: String, block: SceneBuilder.() -> Unit) {
        val b = SceneBuilder().apply(block)
        scenes += Scene(name, b.presetList.toList(), b.sceneDefaults,
            b.renderBlock ?: throw SnapshotException("scene '$name' has no render { } block"))
    }

    internal fun build() = SnapshotApp(scenes.toList(), defaults)
}

/** Per-scene DSL. */
public class SceneBuilder internal constructor() {
    internal val sceneDefaults = SceneDefaults()
    internal val presetList = mutableListOf<String>()
    internal var renderBlock: ((SceneArgs) -> @Composable () -> Unit)? = null

    /** Declare preset names (metadata for --list/validation). */
    public fun presets(vararg names: String) { presetList += names }
    /** Per-scene default dims/theme. */
    public fun defaults(block: SceneDefaults.() -> Unit) { sceneDefaults.block() }
    /** Produce the composable for the given input. */
    public fun render(block: (SceneArgs) -> @Composable () -> Unit) { renderBlock = block }
}

/** Entry point: build a scene registry. */
public fun snapshotApp(block: SnapshotAppBuilder.() -> Unit): SnapshotApp =
    SnapshotAppBuilder().apply(block).build()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*SnapshotAppTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotApp.kt redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/SnapshotAppTest.kt
git commit -m "feat(snapshot): snapshotApp DSL + registry + resolve"
```

---

### Task 6: Differ (per-pixel, tolerance)

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Differ.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DifferTest.kt`

- [ ] **Step 1: Write the failing test**

Create `DifferTest.kt`:
```kotlin
package org.reduxkotlin.snapshot

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DifferTest {
    private fun solid(w: Int, h: Int, rgb: Int): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, rgb)
        val out = ByteArrayOutputStream(); ImageIO.write(img, "png", out); return out.toByteArray()
    }

    @Test fun identical_images_match() {
        val a = solid(10, 10, 0xFF0000)
        val r = Differ().compare(golden = a, actual = a, tolerance = 0, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MATCH, r.verdict); assertEquals(0.0, r.diffPercent)
    }

    @Test fun different_dimensions_mismatch() {
        val r = Differ().compare(solid(10, 10, 0), solid(10, 11, 0), 0, 0.0)
        assertEquals(DiffVerdict.MISMATCH, r.verdict)
    }

    @Test fun within_tolerance_and_under_gate_matches() {
        val a = solid(10, 10, 0x808080); val b = solid(10, 10, 0x828282) // +2/channel
        val r = Differ().compare(a, b, tolerance = 3, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MATCH, r.verdict)
    }

    @Test fun over_gate_mismatches_and_reports_percent() {
        val a = solid(10, 10, 0x000000); val b = solid(10, 10, 0xFFFFFF)
        val r = Differ().compare(a, b, tolerance = 0, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MISMATCH, r.verdict); assertTrue(r.diffPercent > 99.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*DifferTest*"`
Expected: FAIL — `Differ`/`DiffVerdict` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `Differ.kt`:
```kotlin
package org.reduxkotlin.snapshot

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs

/** Diff outcome. */
public enum class DiffVerdict { MATCH, MISMATCH }

/** Diff result: verdict + fraction of pixels exceeding tolerance (0..100). */
public class DiffResult(public val verdict: DiffVerdict, public val diffPercent: Double)

/** Per-pixel image comparator with a per-channel [tolerance] and a [maxDiffPercent] gate. */
public class Differ {
    public fun compare(golden: ByteArray, actual: ByteArray, tolerance: Int, maxDiffPercent: Double): DiffResult {
        val g = ImageIO.read(ByteArrayInputStream(golden))
        val a = ImageIO.read(ByteArrayInputStream(actual))
        if (g.width != a.width || g.height != a.height) return DiffResult(DiffVerdict.MISMATCH, 100.0)
        var differing = 0
        val total = g.width.toLong() * g.height
        for (y in 0 until g.height) for (x in 0 until g.width) {
            val gp = g.getRGB(x, y); val ap = a.getRGB(x, y)
            val dr = abs((gp shr 16 and 0xFF) - (ap shr 16 and 0xFF))
            val dg = abs((gp shr 8 and 0xFF) - (ap shr 8 and 0xFF))
            val db = abs((gp and 0xFF) - (ap and 0xFF))
            if (dr > tolerance || dg > tolerance || db > tolerance) differing++
        }
        val pct = differing * 100.0 / total
        return DiffResult(if (pct > maxDiffPercent) DiffVerdict.MISMATCH else DiffVerdict.MATCH, pct)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*DifferTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/Differ.kt redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DifferTest.kt
git commit -m "feat(snapshot): per-pixel Differ with tolerance + diff-percent gate"
```

> Note (Plan 2): swap/augment this in-house `Differ` with the Roborazzi comparator + diff-image + changed-region boxes per spec §9. The interface (`compare(...) -> DiffResult`) stays; callers don't change.

---

### Task 7: GoldenStore

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/GoldenStore.kt`

- [ ] **Step 1: Write the implementation (covered indirectly by DemoGoldenTest, Task 9)**

Create `GoldenStore.kt`:
```kotlin
package org.reduxkotlin.snapshot

import java.io.File

/** Reads/writes golden PNGs under [dir] keyed by name. */
public class GoldenStore(private val dir: File) {
    /** The golden file for [name] (not guaranteed to exist). */
    public fun goldenFile(name: String): File = File(dir, "$name.png")
    /** Reads golden bytes, or null if absent. */
    public fun read(name: String): ByteArray? = goldenFile(name).takeIf { it.isFile }?.readBytes()
    /** Writes/overwrites the golden for [name]. */
    public fun write(name: String, png: ByteArray) {
        dir.mkdirs(); goldenFile(name).writeBytes(png)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :redux-kotlin-snapshot:compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/GoldenStore.kt
git commit -m "feat(snapshot): GoldenStore read/write"
```

---

### Task 8: Built-in demo scene

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/DemoScene.kt`

- [ ] **Step 1: Write the demo registry (deterministic, no redux, fixed colors/text)**

Create `DemoScene.kt`:
```kotlin
package org.reduxkotlin.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A redux-free, fully deterministic demo registry used to self-test the whole pipe in CI.
 * Solid background + fixed text; no fonts-dependent layout beyond a single label.
 */
public val demoSnapshots: SnapshotApp = snapshotApp {
    defaults { width = 200; height = 100; density = 2f; theme = "dark" }
    scene("demo") {
        presets("default", "light")
        render { args ->
            val bg = if (args.theme == "light") Color.White else Color(0xFF101418)
            val fg = if (args.theme == "light") Color.Black else Color.White
            {
                Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                    Text("snapshot ok", color = fg, fontSize = 20.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :redux-kotlin-snapshot:compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/DemoScene.kt
git commit -m "feat(snapshot): built-in deterministic demo scene"
```

---

### Task 9: Render smoke + determinism + golden self-test

**Files:**
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DemoGoldenTest.kt`
- Create (generated): `redux-kotlin-snapshot/src/test/resources/snapshots/demo-default.png`

- [ ] **Step 1: Write the failing test**

Create `DemoGoldenTest.kt`:
```kotlin
package org.reduxkotlin.snapshot

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DemoGoldenTest {
    private val backend = ImageComposeSceneBackend()

    private fun renderDemo(theme: String): ByteArray {
        val shot = demoSnapshots.resolve("demo", SnapshotInput.Preset("default"), theme, null, null, null)
        return demoSnapshots.renderPng(shot, backend)
    }

    @Test fun renders_nonempty_png_of_expected_size() {
        val png = renderDemo("dark")
        assertTrue(png.isNotEmpty())
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(400, img.width); assertEquals(200, img.height)
    }

    @Test fun render_is_deterministic_within_tolerance() {
        val a = renderDemo("dark"); val b = renderDemo("dark")
        val r = Differ().compare(a, b, tolerance = 0, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MATCH, r.verdict)
    }

    @Test fun matches_committed_golden() {
        val golden = this::class.java.getResourceAsStream("/snapshots/demo-default.png")!!.readBytes()
        val actual = renderDemo("dark")
        // tolerance accommodates AA across machines; the demo is solid bg + one label.
        val r = Differ().compare(golden, actual, tolerance = 8, maxDiffPercent = 1.0)
        assertEquals(DiffVerdict.MATCH, r.verdict, "diff=${r.diffPercent}%")
    }
}
```

- [ ] **Step 2: Run the first two tests (no golden yet)**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*DemoGoldenTest.renders_nonempty*" --tests "*DemoGoldenTest.render_is_deterministic*"`
Expected: PASS (these don't need the golden).

- [ ] **Step 3: Generate the committed golden from the current render**

Add a temporary throwaway generator and run it (do NOT commit the generator):
Create `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/_GenGolden.kt`:
```kotlin
package org.reduxkotlin.snapshot
import java.io.File
import kotlin.test.Test
internal class _GenGolden {
    @Test fun gen() {
        val shot = demoSnapshots.resolve("demo", SnapshotInput.Preset("default"), "dark", null, null, null)
        val png = demoSnapshots.renderPng(shot, ImageComposeSceneBackend())
        File("src/test/resources/snapshots").mkdirs()
        File("src/test/resources/snapshots/demo-default.png").writeBytes(png)
    }
}
```
Run: `./gradlew :redux-kotlin-snapshot:test --tests "*_GenGolden*"`
Then delete the generator: `rm redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/_GenGolden.kt`
Verify the golden exists: `ls -la redux-kotlin-snapshot/src/test/resources/snapshots/demo-default.png`

- [ ] **Step 4: Run the full golden test**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*DemoGoldenTest*"`
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DemoGoldenTest.kt redux-kotlin-snapshot/src/test/resources/snapshots/demo-default.png
git commit -m "test(snapshot): demo render smoke + determinism + golden self-test"
```

> Determinism caveat (spec §6/§9): the committed golden is generated on the dev host. Plan 3 pins a canonical Linux host + bundled font and regenerates goldens there; the `tolerance=8/maxDiffPercent=1.0` here is a deliberately loose self-test, not the production golden contract.

---

### Task 10: CLI — single render + --verify + --list

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt`
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Main.kt`
- Test: `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CliTest.kt`:
```kotlin
package org.reduxkotlin.snapshot

import com.github.ajalt.clikt.testing.test
import org.reduxkotlin.snapshot.cli.snapshotCommand
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CliTest {
    private val tmp = File.createTempFile("rksnap", "").let { it.delete(); it.mkdirs(); it }

    @Test fun list_emits_scene_names() {
        val r = snapshotCommand(demoSnapshots).test("--list")
        assertEquals(0, r.statusCode)
        assertTrue("demo" in r.output)
    }

    @Test fun single_render_writes_a_png() {
        val out = File(tmp, "demo.png")
        val r = snapshotCommand(demoSnapshots).test("--scene demo --preset default --out ${out.path}")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(out.isFile && out.length() > 0)
    }

    @Test fun unknown_scene_exits_2() {
        val r = snapshotCommand(demoSnapshots).test("--scene nope --preset default --out ${File(tmp,"x.png").path}")
        assertEquals(2, r.statusCode)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*CliTest*"`
Expected: FAIL — `snapshotCommand` unresolved.

- [ ] **Step 3: Write the CLI**

Create `cli/Cli.kt`:
```kotlin
package org.reduxkotlin.snapshot.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.parseToJsonElement
import org.reduxkotlin.snapshot.Differ
import org.reduxkotlin.snapshot.DiffVerdict
import org.reduxkotlin.snapshot.ImageComposeSceneBackend
import org.reduxkotlin.snapshot.SnapshotApp
import org.reduxkotlin.snapshot.SnapshotException
import org.reduxkotlin.snapshot.SnapshotInput
import java.io.File

/** Builds the `rk-snapshot` command for [app]. Exit codes: 0 ok, 1 render/verify failure, 2 usage. */
public fun snapshotCommand(app: SnapshotApp): CliktCommand = SnapshotCommand(app)

private class SnapshotCommand(private val app: SnapshotApp) : CliktCommand(name = "rk-snapshot") {
    private val list by option("--list", help = "Print scenes + presets as JSON").flag()
    private val scene by option("--scene")
    private val preset by option("--preset")
    private val stateJson by option("--state-json")
    private val theme by option("--theme")
    private val width by option("--width").int()
    private val height by option("--height").int()
    private val out by option("--out").file()
    private val verify by option("--verify").file()

    override fun run() {
        if (list) { echo(renderList()); return }
        val sceneName = scene ?: throw CliktError("missing --scene (or pass --list)", statusCode = 2)
        if ((preset == null) == (stateJson == null)) {
            throw CliktError("provide exactly one of --preset or --state-json", statusCode = 2)
        }
        val input = preset?.let { SnapshotInput.Preset(it) }
            ?: SnapshotInput.Json(Json.parseToJsonElement(stateJson!!))
        val png = try {
            val shot = app.resolve(sceneName, input, theme, width, height, null)
            app.renderPng(shot, BACKEND)
        } catch (e: SnapshotException) {
            throw CliktError(e.message ?: "usage error", statusCode = 2)
        } catch (e: Throwable) {
            throw CliktError("render failed: ${e.message}", statusCode = 1)
        }
        out?.apply { parentFile?.mkdirs(); writeBytes(png) }
        verify?.let { golden ->
            val r = Differ().compare(golden.readBytes(), png, tolerance = 8, maxDiffPercent = 1.0)
            echo("verify: ${r.verdict} (${"%.3f".format(r.diffPercent)}%)")
            if (r.verdict == DiffVerdict.MISMATCH) throw CliktError("golden mismatch", statusCode = 1)
        }
        if (out != null) echo("wrote ${out!!.path} (${png.size} B)")
    }

    private fun renderList(): String {
        val obj = buildJsonObject {
            put("scenes", buildJsonArray {
                app.scenes.forEach { s ->
                    add(buildJsonObject {
                        put("name", s.name)
                        put("presets", buildJsonArray { s.presets.forEach { add(it) } })
                    })
                }
            })
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), obj)
    }

    private companion object { val BACKEND = ImageComposeSceneBackend() }
}
```

Create `cli/Main.kt`:
```kotlin
package org.reduxkotlin.snapshot.cli

import com.github.ajalt.clikt.core.main
import org.reduxkotlin.snapshot.demoSnapshots

/**
 * Default entry point renders the built-in demo registry. Real apps define their own `main`
 * calling `snapshotCommand(yourRegistry).main(args)`.
 */
public fun main(args: Array<String>) {
    snapshotCommand(demoSnapshots).main(args)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*CliTest*"`
Expected: PASS. (If `CliktError`'s `statusCode` constructor differs in clikt 4.4.0, adjust to the available constructor — verify with `./gradlew :redux-kotlin-snapshot:dependencies | grep clikt` and the clikt 4.4 API.)

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/ redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/CliTest.kt
git commit -m "feat(snapshot): rk-snapshot CLI — single render, --verify, --list"
```

---

### Task 11: assertGolden test helper

**Files:**
- Create: `redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotTestSupport.kt`
- Test: extend `redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DemoGoldenTest.kt`

- [ ] **Step 1: Write the failing test (append to DemoGoldenTest.kt)**

Append to `DemoGoldenTest.kt`:
```kotlin
    @Test fun assertGolden_passes_for_demo() {
        // record mode writes; verify mode asserts. Default = verify against committed golden.
        demoSnapshots.assertGolden(
            scene = "demo", preset = "default", theme = "dark",
            goldenDir = java.io.File("src/test/resources/snapshots"),
            name = "demo-default", tolerance = 8, maxDiffPercent = 1.0, record = false,
        )
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*DemoGoldenTest.assertGolden_passes*"`
Expected: FAIL — `assertGolden` unresolved.

- [ ] **Step 3: Write the implementation**

Create `SnapshotTestSupport.kt`:
```kotlin
package org.reduxkotlin.snapshot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.parseToJsonElement
import java.io.File

/**
 * Renders [scene] and compares to a committed golden under [goldenDir]/[name].png.
 * On mismatch, throws AssertionError and writes actual+diff under build/snapshots/.
 * With [record] = true (or -Dsnapshot.record=true), (over)writes the golden instead of asserting.
 */
public fun SnapshotApp.assertGolden(
    scene: String,
    preset: String? = null,
    json: String? = null,
    theme: String? = null,
    goldenDir: File = File("src/test/resources/snapshots"),
    name: String = if (preset != null) "$scene-$preset" else scene,
    tolerance: Int = 4,
    maxDiffPercent: Double = 0.1,
    record: Boolean = System.getProperty("snapshot.record") == "true",
) {
    require((preset == null) != (json == null)) { "provide exactly one of preset or json" }
    val input = preset?.let { SnapshotInput.Preset(it) } ?: SnapshotInput.Json(Json.parseToJsonElement(json!!))
    val shot = resolve(scene, input, theme, null, null, null)
    val actual = renderPng(shot, ImageComposeSceneBackend())
    val store = GoldenStore(goldenDir)
    if (record) { store.write(name, actual); return }
    val golden = store.read(name)
        ?: throw AssertionError("missing golden '$name' — run with -Dsnapshot.record=true to create it")
    val r = Differ().compare(golden, actual, tolerance, maxDiffPercent)
    if (r.verdict == DiffVerdict.MISMATCH) {
        val dump = File("build/snapshots").apply { mkdirs() }
        File(dump, "$name.actual.png").writeBytes(actual)
        throw AssertionError("snapshot '$name' mismatch: ${r.diffPercent}% (actual written to build/snapshots/)")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :redux-kotlin-snapshot:test --tests "*DemoGoldenTest.assertGolden_passes*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotTestSupport.kt redux-kotlin-snapshot/src/test/kotlin/org/reduxkotlin/snapshot/DemoGoldenTest.kt
git commit -m "feat(snapshot): assertGolden test helper (record/verify)"
```

---

### Task 12: BOM entry + full module build + ABI dump

**Files:**
- Modify: `redux-kotlin-bom/build.gradle.kts`
- Create (generated): `redux-kotlin-snapshot/api/redux-kotlin-snapshot.api`

- [ ] **Step 1: Add the BOM constraint**

In `redux-kotlin-bom/build.gradle.kts`, add `api(project(":redux-kotlin-snapshot"))` (or the constraint form used by the surrounding devtools entries — match the existing experimental block) alongside the other experimental modules.

- [ ] **Step 2: Run the full module build + tests**

Run: `./gradlew :redux-kotlin-snapshot:build`
Expected: `BUILD SUCCESSFUL`; all tests pass.

- [ ] **Step 3: Generate the public ABI dump**

Run: `./gradlew :redux-kotlin-snapshot:updateKotlinAbi` (or `apiDump` — match the task the repo uses; see CLAUDE.md). Then `./gradlew :redux-kotlin-snapshot:checkKotlinAbi`.
Expected: dump created under `redux-kotlin-snapshot/api/`; check passes.

- [ ] **Step 4: Run detekt**

Run: `./gradlew detektAll`
Expected: `BUILD SUCCESSFUL` (KDoc present on all public declarations; fix any `UndocumentedPublic*` by adding KDoc).

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-bom/build.gradle.kts redux-kotlin-snapshot/api/
git commit -m "build(snapshot): add to BOM + commit public ABI dump"
```

---

## Self-review (against the spec)

- **Spec coverage (Plan 1 slice):** engine (§4/§6 — Tasks 2,3), scene DSL + input contract (§5 — Tasks 4,5), per-pixel diff default (§9 — Task 6; Roborazzi upgrade flagged for Plan 2), golden store + assertGolden record/verify (§11 — Tasks 7,11), single-shot CLI + `--list` + structured exit codes (§10 — Task 10), self-test demo scene + render smoke + determinism + golden (§14 — Tasks 8,9), packaging/BOM/ABI + CI-toolchain note (§15 — Tasks 1,12). **Deferred to later plans (noted, not gaps):** batch + report.json + history (§8), semantic dump (§7), HTML dashboard (§12), `--scale`/`--json`/changed-region boxes (§10), action-replay/filmstrip + Android backend (§16 v2), external-consumer module + bundled-font canonical host (§14/§15) → **Plan 2 (CLI/report/semantic/diff-upgrade)** and **Plan 3 (dashboard + consumer integration + determinism hardening)**.
- **Placeholders:** none — every code step has complete code; the two API-uncertainty points (clikt `CliktError.statusCode`, ABI task name) carry explicit verify-and-adjust instructions rather than TODOs.
- **Type consistency:** `RenderSpec(widthDp,heightDp,density,content)`, `RenderBackend.render(spec): ByteArray`, `SnapshotApp.resolve(scene,input,theme,width,height,density)`, `renderPng(shot,backend)`, `Differ.compare(golden,actual,tolerance,maxDiffPercent): DiffResult`, `DiffVerdict.{MATCH,MISMATCH}`, `assertGolden(...)` — names are consistent across Tasks 2–11.

---

## Follow-up plans (scope, not detailed here)

- **Plan 2 — CLI/report/semantic/diff-upgrade:** `--batch` + `report.json` (schema §8) + per-run history; `--scale`, `--json` stdout, structured per-shot errors + batch isolation; semantic/text dump (§7); upgrade `Differ` to the Roborazzi comparator with diff-image + changed-region boxes (§9); `reduxScene { store/seed/content }` sugar + `previewsFrom(...)` `@Preview` source (§5).
- **Plan 3 — dashboard + consumer + determinism:** static HTML dashboard `f(report.json)` (§12); bundled-font + canonical-Linux-host golden contract (§6/§9); TaskFlow reference integration + external-consumer module + CI gate (§14/§15).
