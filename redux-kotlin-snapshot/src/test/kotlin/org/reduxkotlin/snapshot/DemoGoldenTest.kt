package org.reduxkotlin.snapshot

import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DemoGoldenTest {
    // Uses the public render(...) primitive end-to-end.
    private fun renderCounter(preset: String, theme: String = "dark"): ByteArray =
        demoSnapshots.render("counter", SnapshotInput.Preset(preset), theme).png

    @Test fun renders_nonempty_png_of_expected_size() {
        val png = renderCounter("n3")
        assertTrue(png.isNotEmpty())
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(400, img.width) // 200dp * 2
        assertEquals(400, img.height)
    }

    @Test fun render_is_deterministic() {
        val r = Differ().compare(renderCounter("n3"), renderCounter("n3"), tolerance = 0, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MATCH, r.verdict)
    }

    @Test fun density_maps_dp_to_px_exactly_once() {
        // 16.dp padding at density 2 => a 32px background band on top, then the first bar.
        // If density were applied twice (or not at all) these coordinates would not be bg/bar.
        val img = ImageIO.read(ByteArrayInputStream(renderCounter("n3")))
        assertEquals(0x101418, img.getRGB(200, 10) and 0xFFFFFF) // inside the 32px padding band
        assertEquals(0x66AAFF, img.getRGB(200, 40) and 0xFFFFFF) // first bar (after 32px padding)
    }

    @Test fun redux_state_drives_the_ui() {
        // n0 renders zero bars, n3 renders three — proves the UI is a function of dispatched state.
        val r = Differ().compare(renderCounter("n0"), renderCounter("n3"), tolerance = 0, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MISMATCH, r.verdict)
    }

    @Test fun matches_committed_golden() {
        val golden = this::class.java.getResourceAsStream("/snapshots/counter-n3.png")!!.readBytes()
        // counter is non-text (solid bars) -> font-independent -> stable across OS; small tolerance for edge AA.
        val r = Differ().compare(golden, renderCounter("n3"), tolerance = 4, maxDiffPercent = 0.5)
        assertEquals(DiffVerdict.MATCH, r.verdict, "diff=${r.diffPercent}%")
    }

    @Test fun assertGolden_passes_for_counter() {
        demoSnapshots.assertGolden(
            scene = "counter",
            preset = "n3",
            theme = "dark",
            goldenDir = File("src/test/resources/snapshots"),
            name = "counter-n3",
            tolerance = 4,
            maxDiffPercent = 0.5,
            record = false,
        )
    }
}
