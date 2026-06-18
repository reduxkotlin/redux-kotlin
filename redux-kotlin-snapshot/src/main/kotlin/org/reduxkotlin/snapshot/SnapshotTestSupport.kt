package org.reduxkotlin.snapshot

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Renders [scene] and compares it to a committed golden under [goldenDir]/[name].png.
 *
 * With [record] true (or the system property `snapshot.record=true`), (over)writes the golden
 * instead of asserting — the standard "review the diff, then accept" workflow. On mismatch, throws
 * [AssertionError] and writes the actual PNG under `build/snapshots/` for inspection.
 *
 * Exactly one of [preset] or [json] must be provided.
 */
public fun SnapshotApp.assertGolden(
    scene: String,
    preset: String? = null,
    json: String? = null,
    theme: String? = null,
    goldenDir: File = File("src/test/resources/snapshots"),
    name: String = if (preset != null) "$scene-$preset" else scene,
    tolerance: Int = DiffDefaults.TOLERANCE,
    maxDiffPercent: Double = DiffDefaults.STRICT_MAX_DIFF_PERCENT,
    record: Boolean = System.getProperty("snapshot.record") == "true",
) {
    require((preset == null) != (json == null)) { "provide exactly one of preset or json" }
    val input = preset?.let { SnapshotInput.Preset(it) } ?: SnapshotInput.Json(Json.parseToJsonElement(json!!))
    val shot = resolve(scene, input, theme, null, null, null)
    val actual = renderResult(shot, ImageComposeSceneBackend()).png
    val store = GoldenStore(goldenDir)
    if (record) {
        store.write(name, actual)
        return
    }
    val golden = store.read(name)
        ?: throw AssertionError("missing golden '$name' — run with -Dsnapshot.record=true to create it")
    val result = Differ().compare(golden, actual, tolerance, maxDiffPercent)
    if (result.verdict == DiffVerdict.MISMATCH) {
        File("build/snapshots").apply { mkdirs() }
            .let { File(it, "$name.actual.png").writeBytes(actual) }
        throw AssertionError("snapshot '$name' mismatch: ${result.diffPercent}% (actual under build/snapshots/)")
    }
}
