package org.reduxkotlin.sample.taskflow.render

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.reduxkotlin.sample.taskflow.core.Theme
import java.io.File
import kotlin.system.exitProcess

/**
 * `redux-ui render` — the headless render primitive: turn a (screen, state, theme) triple into a
 * PNG on the JVM with no Android, no emulator, no SqlDelight, no network. This is the reusable
 * shape behind the agent loop the architecture spike validated; the in-screen content is purely a
 * function of the seeded Redux state, so an agent can synthesize states and inspect pixels in a
 * tight loop.
 *
 * Usage:
 *   renderUi --screen <settings|board> --out <path.png> [--theme light|dark|system]
 *            [--state <preset>] [--width <px>] [--height <px>]
 *
 * Settings presets: default | offline-failing | online-bot. Board presets: seeded | empty.
 */
public fun main(rawArgs: Array<String>) {
    val args = parseArgs(rawArgs)
    when {
        args.containsKey("list") -> printCapabilities()

        args["batch"] != null -> runBatch(File(args.getValue("batch")))

        else -> renderOne(
            screen = args["screen"] ?: fail("missing --screen (settings|board), or pass --batch <file> / --list"),
            state = args["state"],
            theme = args["theme"],
            out = args["out"],
            width = args["width"]?.toIntOrNull(),
            height = args["height"]?.toIntOrNull(),
            stateJson = resolveStateJson(args["state-json"], args["state-file"]),
        )
    }
    // Compose/skiko leave non-daemon threads alive; force a clean exit once work is done.
    exitProcess(0)
}

/** Renders a single (screen, state, theme) to [out] (defaulted if null) and prints a one-line report. */
private fun renderOne(
    screen: String,
    state: String?,
    theme: String?,
    out: String?,
    width: Int?,
    height: Int?,
    stateJson: JsonElement?,
) {
    val st = state ?: defaultState(screen)
    val th = parseTheme(theme)
    val w = width ?: DEFAULT_WIDTH_PX
    val h = height ?: DEFAULT_HEIGHT_PX
    val label = if (stateJson != null) "json" else st
    val outPath = out ?: "render-$screen-$label-${th.name.lowercase()}.png"
    val content = sceneFor(screen, st, th, stateJson)
    val start = System.nanoTime()
    val png = renderToPng(w, h, content)
    val ms = (System.nanoTime() - start) / 1_000_000
    File(outPath).apply { absoluteFile.parentFile?.mkdirs() }.writeBytes(png)
    println("[redux-ui] screen=$screen state=$label theme=${th.name} ${w}x${h}px -> $outPath (${png.size} B, ${ms}ms)")
}

/** Resolves a screen name + state (preset or agent-supplied [json]) to its composable scene. */
private fun sceneFor(screen: String, state: String, theme: Theme, json: JsonElement?): @Composable () -> Unit =
    when (screen) {
        "settings" -> settingsScene(state, theme, json)
        "board" -> boardScene(state, theme, json)
        else -> fail("unknown --screen '$screen' (settings|board)")
    }

/** The default state preset per screen when `--state` is omitted. */
private fun defaultState(screen: String): String = when (screen) {
    "board" -> "seeded"
    else -> "default"
}

/** Prints the machine-readable capability manifest (screens, presets, themes) for agent discovery. */
private fun printCapabilities() {
    val caps = Capabilities(
        screens = listOf(
            ScreenCap("settings", SETTINGS_PRESETS, Json.parseToJsonElement(SETTINGS_STATE_EXAMPLE)),
            ScreenCap("board", BOARD_PRESETS, Json.parseToJsonElement(BOARD_STATE_EXAMPLE)),
        ),
        themes = listOf("light", "dark", "system"),
    )
    println(Json { prettyPrint = true }.encodeToString(caps))
}

/**
 * Renders every job in a `--batch` file in ONE process. Only the first render pays the skiko
 * classload (~600ms); the rest land at the warm ~50ms — the throughput an agent loop needs.
 */
private fun runBatch(file: File) {
    if (!file.exists()) fail("batch file not found: ${file.absolutePath}")
    val jobs = Json { ignoreUnknownKeys = true }.decodeFromString<List<RenderJob>>(file.readText())
    println("[redux-ui] batch: ${jobs.size} job(s) from ${file.name}")
    jobs.forEach { renderOne(it.screen, it.state, it.theme, it.out, it.width, it.height, it.stateJson) }
}

/** Resolves agent-supplied state: a `--state-file` path wins over inline `--state-json`; null if neither. */
private fun resolveStateJson(inline: String?, file: String?): JsonElement? = when {
    file != null -> Json.parseToJsonElement(File(file).readText())
    inline != null -> Json.parseToJsonElement(inline)
    else -> null
}

internal fun parseTheme(raw: String?): Theme = when (raw?.lowercase()) {
    null, "light" -> Theme.Light
    "dark" -> Theme.Dark
    "system" -> Theme.System
    else -> fail("unknown --theme '$raw' (light|dark|system)")
}

/** Parses `--key value` / `--key=value` flags into a map; bare `--flag` becomes `flag=true`. */
private fun parseArgs(args: Array<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            val key = a.removePrefix("--")
            if (key.contains('=')) {
                map[key.substringBefore('=')] = key.substringAfter('=')
            } else if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                map[key] = args[i + 1]
                i++
            } else {
                map[key] = "true"
            }
        }
        i++
    }
    return map
}

internal fun fail(msg: String): Nothing {
    System.err.println("[redux-ui] error: $msg")
    exitProcess(1)
}

/** One render job in a `--batch` JSON array. */
@Serializable
private data class RenderJob(
    val screen: String,
    val out: String,
    val state: String? = null,
    val theme: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val stateJson: JsonObject? = null,
)

/** A renderable screen, its state presets, and an example agent-authored state, for `--list`. */
@Serializable
private data class ScreenCap(val name: String, val presets: List<String>, val stateExample: JsonElement)

/** The `--list` capability manifest emitted as JSON for agent discovery. */
@Serializable
private data class Capabilities(val screens: List<ScreenCap>, val themes: List<String>)

private val SETTINGS_PRESETS = listOf("default", "offline-failing", "online-bot")
private val BOARD_PRESETS = listOf("seeded", "empty")

private const val SETTINGS_STATE_EXAMPLE =
    """{"theme":"dark","online":false,"botEnabled":true,"failureRate":0.5,"latencyMinMs":0,"latencyMaxMs":1500}"""
private const val BOARD_STATE_EXAMPLE =
    """{"columns":[{"name":"To Do","cards":[""" +
        """{"title":"Write the spec","labels":["docs","backend"],"assignee":"ann"}]},""" +
        """{"name":"Done","cards":[]}]}"""

private const val DEFAULT_WIDTH_PX = 822
private const val DEFAULT_HEIGHT_PX = 1782
