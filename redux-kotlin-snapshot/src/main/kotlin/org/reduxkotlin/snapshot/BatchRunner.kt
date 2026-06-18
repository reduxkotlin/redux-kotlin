package org.reduxkotlin.snapshot

import java.io.File
import kotlin.math.roundToInt

/**
 * Runs a [BatchManifest]: renders each shot to [outDir], optionally verifies against goldens, and
 * returns a [SnapshotReport]. A failing shot is isolated (recorded as `status=error`) and never
 * aborts the run.
 */
internal class BatchRunner(
    private val app: SnapshotApp,
    private val backend: RenderBackend = ImageComposeSceneBackend(),
    private val differ: Differ = Differ(),
) {
    fun run(manifest: BatchManifest, outDir: File, verify: Boolean, goldenDir: File?, runId: String): SnapshotReport {
        outDir.mkdirs()
        val shots = manifest.shots.map { runShot(it, manifest.defaults, outDir, verify, goldenDir) }
        return SnapshotReport(
            runId = runId,
            outDir = outDir.path,
            totals = Totals(
                total = shots.size,
                ok = shots.count { it.status == "ok" && it.verify?.result != "mismatch" },
                failed = shots.count { it.status == "error" },
                mismatched = shots.count { it.verify?.result == "mismatch" },
                missingGolden = shots.count { it.verify?.result == "missing-golden" },
            ),
            shots = shots,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runShot(
        spec: ShotSpec,
        defaults: ManifestDefaults,
        outDir: File,
        verify: Boolean,
        goldenDir: File?,
    ): ShotReport {
        val inputDesc = if (spec.preset != null) "preset=${spec.preset}" else "json"
        return try {
            val input = when {
                spec.preset != null -> SnapshotInput.Preset(spec.preset)
                spec.stateJson != null -> SnapshotInput.Json(spec.stateJson)
                else -> throw SnapshotException("shot '${spec.id}' needs 'preset' or 'stateJson'")
            }
            val shot = app.resolve(
                spec.scene,
                input,
                theme = spec.theme ?: defaults.theme,
                width = spec.width ?: defaults.width,
                height = spec.height ?: defaults.height,
                density = spec.density ?: defaults.density,
            )
            val start = System.currentTimeMillis()
            val png = app.renderResult(shot, backend).png
            val ms = System.currentTimeMillis() - start
            val outFile = File(outDir, spec.out ?: "${spec.id}.png").apply {
                parentFile?.mkdirs()
                writeBytes(png)
            }
            ShotReport(
                id = spec.id, scene = spec.scene, input = inputDesc, theme = shot.theme,
                sizePx = listOf(
                    (shot.widthDp * shot.density).roundToInt(),
                    (shot.heightDp * shot.density).roundToInt(),
                ),
                out = outFile.path, bytes = png.size, renderMs = ms, status = "ok",
                verify = if (verify) verifyShot(spec, png, goldenDir, outDir) else null,
            )
        } catch (e: Exception) {
            ShotReport(id = spec.id, scene = spec.scene, input = inputDesc, status = "error", error = e.message)
        }
    }

    private fun verifyShot(spec: ShotSpec, png: ByteArray, goldenDir: File?, outDir: File): VerifyReport {
        val goldenFile = File(goldenDir ?: File("."), "${spec.id}.png")
        val golden = goldenFile.takeIf { it.isFile }?.readBytes()
            ?: return VerifyReport(goldenFile.path, "missing-golden", 0.0)
        val r = differ.compare(golden, png, DiffDefaults.TOLERANCE, DiffDefaults.BATCH_MAX_DIFF_PERCENT)
        return if (r.verdict == DiffVerdict.MATCH) {
            VerifyReport(goldenFile.path, "match", r.diffPercent)
        } else {
            val diffPath = differ.diffImage(golden, png, DiffDefaults.TOLERANCE)
                ?.let { File(outDir, "${spec.id}.diff.png").apply { writeBytes(it) }.path }
            VerifyReport(goldenFile.path, "mismatch", r.diffPercent, diffPath)
        }
    }
}
