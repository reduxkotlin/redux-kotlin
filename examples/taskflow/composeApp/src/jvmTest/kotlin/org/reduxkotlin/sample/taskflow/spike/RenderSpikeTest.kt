package org.reduxkotlin.sample.taskflow.spike

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import org.reduxkotlin.Store
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.multimodel.ModelState
import org.reduxkotlin.sample.taskflow.app.createAppStore
import org.reduxkotlin.sample.taskflow.core.Theme
import org.reduxkotlin.sample.taskflow.feature.settings.SetBotEnabled
import org.reduxkotlin.sample.taskflow.feature.settings.SetFailureRate
import org.reduxkotlin.sample.taskflow.feature.settings.SetLatency
import org.reduxkotlin.sample.taskflow.feature.settings.SetOnline
import org.reduxkotlin.sample.taskflow.feature.settings.SetTheme
import org.reduxkotlin.sample.taskflow.feature.settings.SettingsScreen
import org.reduxkotlin.sample.taskflow.ui.theme.TaskFlowTheme
import java.io.File
import kotlin.test.assertTrue

/**
 * THESIS-VALIDATION SPIKE (not a real test): can a Compose screen be rendered headlessly on the
 * JVM purely as a function of hand-seeded Redux state — no Android, no SqlDelight, no network, no
 * emulator — fast enough for an agent loop, with legible pixels?
 *
 * Each variant builds a root store via [createAppStore] with [NotificationContext.Inline], seeds a
 * distinct [org.reduxkotlin.sample.taskflow.core.AppSettingsModel] by dispatching settings actions,
 * then renders [SettingsScreen] off-screen and encodes a PNG. The seeded `theme` drives BOTH the
 * in-screen segmented-button selection AND the [TaskFlowTheme] color scheme, so different state ->
 * visibly different pixels. Timings (cold first render + warm renders) are printed; PNGs land in
 * build/render-spike for human/VLM inspection.
 */
@OptIn(ExperimentalTestApi::class)
class RenderSpikeTest {

    private val outDir = File("build/render-spike").apply { mkdirs() }

    private data class Variant(val name: String, val seed: (Store<ModelState>) -> Unit, val theme: Theme)

    private val variants = listOf(
        Variant("light-default", seed = {}, theme = Theme.Light),
        Variant("dark-default", seed = { it.dispatch(SetTheme(Theme.Dark)) }, theme = Theme.Dark),
        Variant(
            "dark-offline-failing",
            seed = {
                it.dispatch(SetTheme(Theme.Dark))
                it.dispatch(SetOnline(false))
                it.dispatch(SetBotEnabled(false))
                it.dispatch(SetFailureRate(0.9f))
                it.dispatch(SetLatency(0, 3000))
            },
            theme = Theme.Dark,
        ),
        Variant(
            "light-online-bot",
            seed = {
                it.dispatch(SetTheme(Theme.Light))
                it.dispatch(SetOnline(true))
                it.dispatch(SetBotEnabled(true))
                it.dispatch(SetFailureRate(0.0f))
            },
            theme = Theme.Light,
        ),
    )

    @Test
    fun renderSettingsAcrossSeededStates() {
        val timingsMs = mutableListOf<Pair<String, Long>>()

        variants.forEachIndexed { index, v ->
            val start = System.nanoTime()
            renderVariant(v)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            timingsMs += v.name to elapsedMs
            // index 0 is the cold render (Compose/skiko classload + first composition warmup).
            println("[render-spike] ${v.name}: ${elapsedMs}ms ${if (index == 0) "(cold)" else "(warm)"}")
        }

        val warm = timingsMs.drop(1).map { it.second }
        if (warm.isNotEmpty()) {
            println("[render-spike] warm avg: ${warm.average().toLong()}ms  min=${warm.min()}ms max=${warm.max()}ms")
        }
        println("[render-spike] PNGs written to: ${outDir.absolutePath}")

        variants.forEach { v ->
            val f = File(outDir, "${v.name}.png")
            assertTrue(f.exists() && f.length() > 0, "expected non-empty PNG for ${v.name}")
        }
    }

    private fun renderVariant(v: Variant) = runComposeUiTest {
        val store = createAppStore(NotificationContext.Inline)
        v.seed(store)

        setContent {
            // Fixed phone-ish canvas so the captured root has known, non-zero bounds.
            Box(modifier = Modifier.size(411.dp, 891.dp)) {
                Screen(store = store, theme = v.theme)
            }
        }
        waitForIdle()

        val bmp = onRoot().captureToImage()
        val pngBytes = Image.makeFromBitmap(bmp.asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)!!
            .bytes
        File(outDir, "${v.name}.png").writeBytes(pngBytes)
    }

    @Composable
    private fun Screen(store: Store<ModelState>, theme: Theme) {
        TaskFlowTheme(theme = theme, dynamic = false) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SettingsScreen(store)
            }
        }
    }
}
