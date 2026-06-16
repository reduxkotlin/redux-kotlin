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
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    @Test fun identical_images_match() {
        val a = solid(10, 10, 0xFF0000)
        val r = Differ().compare(a, a, tolerance = 0, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MATCH, r.verdict)
        assertEquals(0.0, r.diffPercent)
    }

    @Test fun different_dimensions_mismatch() {
        val r = Differ().compare(solid(10, 10, 0), solid(10, 11, 0), 0, 0.0)
        assertEquals(DiffVerdict.MISMATCH, r.verdict)
    }

    @Test fun within_tolerance_and_under_gate_matches() {
        val a = solid(10, 10, 0x808080)
        val b = solid(10, 10, 0x828282) // +2 per channel
        val r = Differ().compare(a, b, tolerance = 3, maxDiffPercent = 0.0)
        assertEquals(DiffVerdict.MATCH, r.verdict)
    }

    @Test fun over_gate_mismatches_and_reports_percent() {
        val r = Differ().compare(solid(10, 10, 0x000000), solid(10, 10, 0xFFFFFF), 0, 0.0)
        assertEquals(DiffVerdict.MISMATCH, r.verdict)
        assertTrue(r.diffPercent > 99.0)
    }
}
