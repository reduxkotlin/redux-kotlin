package org.reduxkotlin.snapshot.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.snapshot.BatchManifest
import org.reduxkotlin.snapshot.BatchRunner
import org.reduxkotlin.snapshot.DashboardGenerator
import org.reduxkotlin.snapshot.DiffDefaults
import org.reduxkotlin.snapshot.DiffVerdict
import org.reduxkotlin.snapshot.Differ
import org.reduxkotlin.snapshot.ImageComposeSceneBackend
import org.reduxkotlin.snapshot.RenderResult
import org.reduxkotlin.snapshot.SemanticsDiffer
import org.reduxkotlin.snapshot.SnapshotApp
import org.reduxkotlin.snapshot.SnapshotException
import org.reduxkotlin.snapshot.SnapshotInput
import org.reduxkotlin.snapshot.SnapshotReport
import java.io.File

/**
 * Builds the `snapshot` command for [app]. Exit codes: 0 ok, 1 render/verify failure, 2 usage.
 * Public so the unified `rk` CLI can mount it as a subcommand; library consumers building their
 * OWN binary should call [runCli] instead — it avoids leaking the [CliktCommand] return type and
 * the Clikt dependency into their public API.
 */
public fun snapshotCommand(app: SnapshotApp): CliktCommand = SnapshotCommand(app)

/**
 * Runs the snapshot CLI for this registry — the entry point a consuming app calls from its own
 * `main`. Keeps Clikt types from leaking across the module boundary.
 *
 * Callers should follow this with `exitProcess(0)`: Skiko / Compose leave non-daemon threads alive
 * after the command returns, which would prevent the process from exiting cleanly.
 */
public fun SnapshotApp.runCli(argv: Array<String>) {
    snapshotCommand(this).main(argv)
}

private class SnapshotCommand(private val app: SnapshotApp) : CliktCommand(name = "snapshot") {
    private val list by option("--list", help = "Print scenes + presets as JSON").flag()
    private val scene by option("--scene", help = "Scene name")
    private val preset by option("--preset", help = "Preset name")
    private val stateJson by option("--state-json", help = "Inline JSON state (scene decodes it)")
    private val theme by option("--theme", help = "Theme name")
    private val width by option("--width", help = "Width in dp").int()
    private val height by option("--height", help = "Height in dp").int()
    private val out by option("--out", help = "Output PNG path").file()
    private val verify by option("--verify", help = "Golden PNG to compare against (single shot)").file()
    private val batch by option("--batch", help = "Batch manifest JSON (see --list for scenes/presets)").file()
    private val outDir by option("--out-dir", help = "Batch output directory").file().default(File(".rk-snapshots"))
    private val goldenDir by option("--golden-dir", help = "Golden dir; its presence verifies the batch").file()
    private val jsonMode by option("--json", help = "Emit machine JSON on stdout").flag()
    private val dashboard by option("--dashboard", help = "Also write a static index.html over the report").flag()
    private val semantics by option(
        "--semantics",
        help = "Emit the semantics dump (stdout single; sidecar batch)",
    ).flag()
    private val semanticsFormat by option("--semantics-format", help = "Dump format")
        .choice("json", "text").default("text")
    private val verifySemanticsFile by option(
        "--verify-semantics-file",
        help = "Semantics golden to compare against (single shot)",
    ).file()
    private val verifySemantics by option(
        "--verify-semantics",
        help = "Enable the semantics golden gate (batch; needs --golden-dir)",
    ).flag()
    private val updateSemantics by option(
        "--update-semantics",
        help = "Write/update semantics golden(s), then exit 0",
    ).flag()

    override fun run() {
        val b = batch
        when {
            list -> echo(renderList())
            b != null -> runBatch(b)
            else -> runSingle()
        }
    }

    // Converting any render failure into a clean CLI error (exit 1) is the intended "never crash"
    // contract. Sub-steps (parse input, render, each gate) are split into helpers below to keep
    // this orchestration method within the complexity/length budget.
    private fun runSingle() {
        val sceneName = scene ?: throw CliktError("missing --scene (or pass --list)", statusCode = 2)
        if ((preset == null) == (stateJson == null)) {
            throw CliktError("provide exactly one of --preset or --state-json", statusCode = 2)
        }
        val result = renderSingle(sceneName, parseSingleInput())
        val png = result.png
        out?.apply {
            parentFile?.mkdirs()
            writeBytes(png)
        }

        val canonical = result.semantics.toCanonicalJson()
        if (updateSemantics) {
            writeSemanticsGolden(canonical)
            return
        }
        emitSemanticsDump(result, canonical)
        verifySemanticsGate(canonical)
        verifyPixelGate(png)
        out?.let { echo("wrote ${it.path} (${png.size} B)") }
    }

    private fun parseSingleInput(): SnapshotInput = try {
        preset?.let { SnapshotInput.Preset(it) }
            ?: SnapshotInput.Json(Json.parseToJsonElement(stateJson!!))
    } catch (e: SerializationException) {
        throw CliktError("invalid --state-json: ${e.message}", cause = e, statusCode = 2)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun renderSingle(sceneName: String, input: SnapshotInput) = try {
        val shot = app.resolve(sceneName, input, theme, width, height, null)
        app.renderResult(shot, BACKEND)
    } catch (e: SnapshotException) {
        throw CliktError(e.message ?: "usage error", cause = e, statusCode = 2)
    } catch (e: Exception) {
        throw CliktError("render failed: ${e.message}", cause = e, statusCode = 1)
    }

    private fun writeSemanticsGolden(canonical: String) {
        val golden = verifySemanticsFile
            ?: throw CliktError("--update-semantics needs --verify-semantics-file", statusCode = 2)
        golden.parentFile?.mkdirs()
        golden.writeText(canonical)
        echo("wrote semantics golden ${golden.path}")
    }

    private fun emitSemanticsDump(result: RenderResult, canonical: String) {
        if (!semantics) return
        val dump = if (semanticsFormat == "json") canonical else result.semantics.toText()
        val sidecarExt = if (semanticsFormat == "json") ".semantics.json" else ".semantics.txt"
        out?.let { File(it.path + sidecarExt).writeText(dump) }
        echo(dump)
    }

    private fun verifySemanticsGate(canonical: String) {
        val golden = verifySemanticsFile ?: return
        if (!golden.isFile) {
            throw CliktError(
                "--verify-semantics-file golden not found: ${golden.absolutePath}\n" +
                    "  Generate it first with --update-semantics --verify-semantics-file ${golden.path}.",
                statusCode = 2,
            )
        }
        val r = SemanticsDiffer().compare(golden.readText(), canonical)
        echo("verify-semantics: ${r.result}")
        if (r.result == "mismatch") {
            r.delta.forEach { echo(it) }
            throw CliktError("semantics golden mismatch", statusCode = 1)
        }
    }

    private fun verifyPixelGate(png: ByteArray) {
        val golden = verify ?: return
        if (!golden.isFile) {
            throw CliktError(
                "--verify golden not found: ${golden.absolutePath}\n" +
                    "  Generate it first with --out ${golden.path} (no --verify), then re-run with --verify.",
                statusCode = 2,
            )
        }
        val r = Differ().compare(
            golden.readBytes(),
            png,
            tolerance = DiffDefaults.TOLERANCE,
            maxDiffPercent = DiffDefaults.BATCH_MAX_DIFF_PERCENT,
        )
        echo("verify: ${r.verdict} (${"%.3f".format(r.diffPercent)}%)")
        if (r.verdict == DiffVerdict.MISMATCH) throw CliktError("golden mismatch", statusCode = 1)
    }

    private fun runBatch(file: File) {
        requireManifest(file)
        requireBatchSemanticsGuards(verifySemantics, updateSemantics, goldenDir)
        val manifest = try {
            Json.decodeFromString(BatchManifest.serializer(), file.readText())
        } catch (e: SerializationException) {
            throw CliktError("invalid --batch manifest: ${e.message}", cause = e, statusCode = 2)
        }
        val runId = "run-${System.currentTimeMillis()}"
        val report = BatchRunner(app, BACKEND).run(
            manifest, outDir, verify = goldenDir != null && !updateSemantics, goldenDir, runId,
            semantics = semantics, verifySemantics = verifySemantics,
            updateSemantics = updateSemantics, semanticsFormat = semanticsFormat,
        )
        val json = Json { prettyPrint = true }.encodeToString(SnapshotReport.serializer(), report)
        File(outDir, "report.json").apply {
            parentFile?.mkdirs()
            writeText(json)
        }
        if (dashboard) {
            val index = DashboardGenerator.generate(report, outDir)
            if (!jsonMode) echo("dashboard: ${index.path}")
        }
        if (jsonMode) {
            echo(json)
        } else {
            val t = report.totals
            echo(
                "batch: ${t.ok}/${t.total} ok, ${t.failed} failed, ${t.mismatched} mismatched, " +
                    "${t.semanticsMismatched} semantics-mismatched -> ${outDir.path}",
            )
            echoDrift(report) { echo(it) }
        }
        // --update-semantics (re)writes each shot's canonical form and exits 0 without gating —
        // BatchRunner already wrote the semantics goldens above; the pixel/semantics failure gate
        // below only applies to normal and --verify-semantics runs.
        if (updateSemantics) return
        if (report.totals.failed > 0 || report.totals.mismatched > 0 || report.totals.semanticsMismatched > 0) {
            throw CliktError(
                "batch had ${report.totals.failed} failed / ${report.totals.mismatched} mismatched / " +
                    "${report.totals.semanticsMismatched} semantics-mismatched",
                statusCode = 1,
            )
        }
    }

    // Fail with a fix-it message (path + cwd + sample manifest) instead of a raw FileNotFoundException —
    // the path is relative to the JVM's working dir, which is rarely where the human typed the command.
    private fun requireManifest(file: File) {
        if (file.isFile) return
        val sample = app.scenes.firstOrNull()
        val shotLine = sample?.let {
            val p = it.presets.firstOrNull() ?: "default"
            """    { "id": "${it.name}-$p", "scene": "${it.name}", "preset": "$p" }"""
        } ?: """    { "id": "shot1", "scene": "<scene>", "preset": "<preset>" }"""
        throw CliktError(
            buildString {
                appendLine("--batch manifest not found: ${file.absolutePath}")
                appendLine("  (relative paths resolve against the working dir: ${File("").absolutePath})")
                appendLine("Fix — create that JSON file, e.g.:")
                appendLine("""  { "shots": [""")
                appendLine(shotLine)
                appendLine("  ] }")
                append("Run with --list to see all scenes + presets, ")
                append("or render a single shot: --scene <name> --preset <preset> --out shot.png")
            },
            statusCode = 2,
        )
    }

    private fun renderList(): String {
        val obj = buildJsonObject {
            put(
                "scenes",
                buildJsonArray {
                    app.scenes.forEach { s ->
                        add(
                            buildJsonObject {
                                put("name", s.name)
                                put("presets", buildJsonArray { s.presets.forEach { add(it) } })
                            },
                        )
                    }
                },
            )
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), obj)
    }

    private companion object {
        val BACKEND = ImageComposeSceneBackend()
    }
}

// Kept as a top-level function (rather than a SnapshotCommand member) so the batch semantics
// guard doesn't push the command class over detekt's TooManyFunctions budget, and so runBatch's
// own ThrowsCount stays within the two-throw limit.
private fun requireBatchSemanticsGuards(verifySemantics: Boolean, updateSemantics: Boolean, goldenDir: File?) {
    if (verifySemantics && goldenDir == null) {
        throw CliktError("--verify-semantics needs --golden-dir", statusCode = 2)
    }
    if (updateSemantics && goldenDir == null) {
        throw CliktError("--update-semantics (batch) needs --golden-dir", statusCode = 2)
    }
}

// Terse drift-only block: one line per shot that errored or mismatched (pixel or semantics),
// with any semantics delta lines indented beneath it. Top-level (not a SnapshotCommand member)
// for the same TooManyFunctions/ThrowsCount budget reasons as [requireBatchSemanticsGuards].
private fun echoDrift(report: SnapshotReport, echo: (String) -> Unit) {
    report.shots.filter {
        it.status == "error" || it.verify?.result == "mismatch" || it.verifySemantics?.result == "mismatch"
    }.forEach { s ->
        echo(
            "  drift ${s.id}: pixel=${s.verify?.result ?: "-"} semantics=${s.verifySemantics?.result ?: "-"} " +
                "png=${s.out ?: "-"} diff=${s.verify?.diffImage ?: "-"} sidecar=${s.semanticsSidecar ?: "-"}",
        )
        s.verifySemantics?.delta?.forEach { echo("    $it") }
    }
}
