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
    private val semanticsDiffer: SemanticsDiffer = SemanticsDiffer(),
) {
    @Suppress("LongParameterList")
    fun run(
        manifest: BatchManifest,
        outDir: File,
        verify: Boolean,
        goldenDir: File?,
        runId: String,
        semantics: Boolean = false,
        verifySemantics: Boolean = false,
        updateSemantics: Boolean = false,
        semanticsFormat: String = "text",
    ): SnapshotReport {
        outDir.mkdirs()
        val shots = manifest.shots.map {
            runShot(
                it, manifest.defaults, outDir, verify, goldenDir,
                semantics, verifySemantics, updateSemantics, semanticsFormat,
            )
        }
        return SnapshotReport(
            runId = runId,
            outDir = outDir.path,
            totals = Totals(
                total = shots.size,
                ok = shots.count {
                    it.status == "ok" && it.verify?.result != "mismatch" && it.verifySemantics?.result != "mismatch"
                },
                failed = shots.count { it.status == "error" },
                mismatched = shots.count { it.verify?.result == "mismatch" },
                missingGolden = shots.count { it.verify?.result == "missing-golden" },
                semanticsMismatched = shots.count { it.verifySemantics?.result == "mismatch" },
                semanticsMissingGolden = shots.count { it.verifySemantics?.result == "missing-golden" },
                semanticsMatched = shots.count { it.verifySemantics?.result == "match" },
                renderMsTotal = shots.sumOf { it.renderMs ?: 0 },
            ),
            shots = shots,
        )
    }

    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    private fun runShot(
        spec: ShotSpec,
        defaults: ManifestDefaults,
        outDir: File,
        verify: Boolean,
        goldenDir: File?,
        semantics: Boolean,
        verifySemantics: Boolean,
        updateSemantics: Boolean,
        semanticsFormat: String,
    ): ShotReport {
        val inputDesc = if (spec.preset != null) "preset=${spec.preset}" else "json"
        return try {
            val input = resolveInput(spec)
            val shot = app.resolve(
                spec.scene,
                input,
                theme = spec.theme ?: defaults.theme,
                width = spec.width ?: defaults.width,
                height = spec.height ?: defaults.height,
                density = spec.density ?: defaults.density,
            )
            val start = System.currentTimeMillis()
            val result = app.renderResult(shot, backend)
            val ms = System.currentTimeMillis() - start
            val png = result.png
            val outFile = File(outDir, spec.out ?: "${spec.id}.png").apply {
                parentFile?.mkdirs()
                writeBytes(png)
            }
            val canonical = result.semantics.toCanonicalJson()

            val (sidecar, semVerify) = applySemanticsExtras(
                spec, result.semantics, canonical, outDir, goldenDir,
                semantics, verifySemantics, updateSemantics, semanticsFormat,
            )

            ShotReport(
                id = spec.id, scene = spec.scene, input = inputDesc, theme = shot.theme,
                sizePx = listOf(
                    (shot.widthDp * shot.density).roundToInt(),
                    (shot.heightDp * shot.density).roundToInt(),
                ),
                out = outFile.path, bytes = png.size, renderMs = ms, status = "ok",
                verify = if (verify) verifyShot(spec, png, goldenDir, outDir) else null,
                verifySemantics = semVerify,
                semanticsSidecar = sidecar?.path,
                semanticsBytes = if (semantics || verifySemantics) canonical.toByteArray().size else null,
            )
        } catch (e: Exception) {
            ShotReport(id = spec.id, scene = spec.scene, input = inputDesc, status = "error", error = e.message)
        }
    }

    /** Resolves a [ShotSpec] into the [SnapshotInput] the app should render, or throws if neither is set. */
    private fun resolveInput(spec: ShotSpec): SnapshotInput = when {
        spec.preset != null -> SnapshotInput.Preset(spec.preset)
        spec.stateJson != null -> SnapshotInput.Json(spec.stateJson)
        else -> throw SnapshotException("shot '${spec.id}' needs 'preset' or 'stateJson'")
    }

    /**
     * Handles the golden-update, sidecar-write, and semantics-verify trio for a rendered shot.
     * Returns the sidecar file (if written) and the semantics verify report (if requested).
     */
    @Suppress("LongParameterList")
    private fun applySemanticsExtras(
        spec: ShotSpec,
        dump: SemanticsDump,
        canonical: String,
        outDir: File,
        goldenDir: File?,
        semantics: Boolean,
        verifySemantics: Boolean,
        updateSemantics: Boolean,
        semanticsFormat: String,
    ): Pair<File?, SemanticsVerifyReport?> {
        if (updateSemantics && goldenDir != null) {
            File(goldenDir, "${spec.id}.semantics.json").apply {
                parentFile?.mkdirs()
                writeText(canonical)
            }
        }
        val sidecar = if (semantics) writeSidecar(spec, dump, canonical, outDir, semanticsFormat) else null
        val semVerify = if (verifySemantics) verifySemanticsShot(spec, canonical, goldenDir) else null
        return sidecar to semVerify
    }

    private fun writeSidecar(
        spec: ShotSpec,
        dump: SemanticsDump,
        canonical: String,
        outDir: File,
        format: String,
    ): File {
        val ext = if (format == "json") "semantics.json" else "semantics.txt"
        val body = if (format == "json") canonical else dump.toText()
        return File(outDir, "${spec.id}.$ext").apply {
            parentFile?.mkdirs()
            writeText(body)
        }
    }

    private fun verifySemanticsShot(spec: ShotSpec, canonical: String, goldenDir: File?): SemanticsVerifyReport {
        val goldenFile = File(goldenDir ?: File("."), "${spec.id}.semantics.json")
        val golden = goldenFile.takeIf { it.isFile }?.readText()
            ?: return SemanticsVerifyReport(goldenFile.path, "missing-golden")
        val r = semanticsDiffer.compare(golden, canonical)
        return SemanticsVerifyReport(goldenFile.path, r.result, r.delta.takeIf { it.isNotEmpty() })
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
