package org.reduxkotlin.snapshot.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.reduxkotlin.snapshot.DiffVerdict
import org.reduxkotlin.snapshot.Differ
import org.reduxkotlin.snapshot.ImageComposeSceneBackend
import org.reduxkotlin.snapshot.SnapshotApp
import org.reduxkotlin.snapshot.SnapshotException
import org.reduxkotlin.snapshot.SnapshotInput

/**
 * Builds the `rk-snapshot` command for [app]. Exit codes: 0 ok, 1 render/verify failure, 2 usage.
 */
public fun snapshotCommand(app: SnapshotApp): CliktCommand = SnapshotCommand(app)

private class SnapshotCommand(private val app: SnapshotApp) : CliktCommand(name = "rk-snapshot") {
    private val list by option("--list", help = "Print scenes + presets as JSON").flag()
    private val scene by option("--scene", help = "Scene name")
    private val preset by option("--preset", help = "Preset name")
    private val stateJson by option("--state-json", help = "Inline JSON state (scene decodes it)")
    private val theme by option("--theme", help = "Theme name")
    private val width by option("--width", help = "Width in dp").int()
    private val height by option("--height", help = "Height in dp").int()
    private val out by option("--out", help = "Output PNG path").file()
    private val verify by option("--verify", help = "Golden PNG to compare against").file()

    // Converting any render failure into a clean CLI error (exit 1) is the intended "never crash"
    // contract; the multiple throws are usage/validation guards. Both are idiomatic for a CLI.
    @Suppress("ThrowsCount", "TooGenericExceptionCaught")
    override fun run() {
        if (list) {
            echo(renderList())
            return
        }
        val sceneName = scene ?: throw CliktError("missing --scene (or pass --list)", statusCode = 2)
        if ((preset == null) == (stateJson == null)) {
            throw CliktError("provide exactly one of --preset or --state-json", statusCode = 2)
        }
        val input = try {
            preset?.let { SnapshotInput.Preset(it) }
                ?: SnapshotInput.Json(Json.parseToJsonElement(stateJson!!))
        } catch (e: SerializationException) {
            throw CliktError("invalid --state-json: ${e.message}", cause = e, statusCode = 2)
        }
        val png = try {
            val shot = app.resolve(sceneName, input, theme, width, height, null)
            app.renderResult(shot, BACKEND).png
        } catch (e: SnapshotException) {
            throw CliktError(e.message ?: "usage error", cause = e, statusCode = 2)
        } catch (e: Exception) {
            throw CliktError("render failed: ${e.message}", cause = e, statusCode = 1)
        }
        out?.apply {
            parentFile?.mkdirs()
            writeBytes(png)
        }
        verify?.let { golden ->
            val r = Differ().compare(golden.readBytes(), png, tolerance = 8, maxDiffPercent = 1.0)
            echo("verify: ${r.verdict} (${"%.3f".format(r.diffPercent)}%)")
            if (r.verdict == DiffVerdict.MISMATCH) throw CliktError("golden mismatch", statusCode = 1)
        }
        out?.let { echo("wrote ${it.path} (${png.size} B)") }
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
