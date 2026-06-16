package org.reduxkotlin.snapshot

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Single source for the comparison thresholds, so the test/batch/CLI entry points stay aligned.
 * [TOLERANCE] is the per-channel 8-bit delta below which two pixels count as equal; the percent
 * gate is the share of differing pixels tolerated before a MISMATCH.
 */
internal object DiffDefaults {
    /** Per-channel (0..255) delta below which a pixel is considered unchanged. */
    const val TOLERANCE: Int = 4

    /** Unit-test gate (`assertGolden`): fail on the slightest drift. */
    const val STRICT_MAX_DIFF_PERCENT: Double = 0.1

    /** Batch / CLI `--verify` gate: tolerate sub-pixel anti-aliasing noise across many shots. */
    const val BATCH_MAX_DIFF_PERCENT: Double = 0.5
}

/** Diff outcome. */
public enum class DiffVerdict {
    /** Images are equal within tolerance and under the diff gate. */
    MATCH,

    /** Images differ beyond tolerance/gate or have different dimensions. */
    MISMATCH,
}

/** Result of a comparison: the [verdict] and the fraction of differing pixels [diffPercent] (0..100). */
public class DiffResult(
    /** Whether the images matched. */
    public val verdict: DiffVerdict,
    /** Percentage of pixels exceeding tolerance (0..100). */
    public val diffPercent: Double,
)

/**
 * Per-pixel image comparator with a per-channel [tolerance] and a [maxDiffPercent] gate.
 *
 * (A later plan upgrades this to the Roborazzi comparator with diff-image + changed-region output;
 * the `compare(...) -> DiffResult` contract is kept stable so callers do not change.)
 */
public class Differ {
    /** Compares [golden] vs [actual] PNG bytes. */
    public fun compare(golden: ByteArray, actual: ByteArray, tolerance: Int, maxDiffPercent: Double): DiffResult {
        val g = ImageIO.read(ByteArrayInputStream(golden))
        val a = ImageIO.read(ByteArrayInputStream(actual))
        if (g.width != a.width || g.height != a.height) return DiffResult(DiffVerdict.MISMATCH, 100.0)
        var differing = 0L
        val total = g.width.toLong() * g.height
        for (y in 0 until g.height) {
            for (x in 0 until g.width) {
                val gp = g.getRGB(x, y)
                val ap = a.getRGB(x, y)
                val dr = abs((gp shr 16 and 0xFF) - (ap shr 16 and 0xFF))
                val dg = abs((gp shr 8 and 0xFF) - (ap shr 8 and 0xFF))
                val db = abs((gp and 0xFF) - (ap and 0xFF))
                if (dr > tolerance || dg > tolerance || db > tolerance) differing++
            }
        }
        val pct = differing * 100.0 / total
        return DiffResult(if (pct > maxDiffPercent) DiffVerdict.MISMATCH else DiffVerdict.MATCH, pct)
    }

    /**
     * Renders a diff PNG: changed pixels (per-channel delta > [tolerance]) painted magenta over a
     * dimmed copy of [actual]. Returns null if the images have different dimensions.
     */
    public fun diffImage(golden: ByteArray, actual: ByteArray, tolerance: Int): ByteArray? {
        val g = ImageIO.read(ByteArrayInputStream(golden))
        val a = ImageIO.read(ByteArrayInputStream(actual))
        if (g.width != a.width || g.height != a.height) return null
        val out = BufferedImage(a.width, a.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val gp = g.getRGB(x, y)
                val ap = a.getRGB(x, y)
                val changed = abs((gp shr 16 and 0xFF) - (ap shr 16 and 0xFF)) > tolerance ||
                    abs((gp shr 8 and 0xFF) - (ap shr 8 and 0xFF)) > tolerance ||
                    abs((gp and 0xFF) - (ap and 0xFF)) > tolerance
                out.setRGB(x, y, if (changed) MAGENTA else dim(ap))
            }
        }
        val bytes = ByteArrayOutputStream()
        ImageIO.write(out, "png", bytes)
        return bytes.toByteArray()
    }

    private fun dim(rgb: Int): Int {
        val r = (rgb shr 16 and 0xFF) / 4
        val gg = (rgb shr 8 and 0xFF) / 4
        val b = (rgb and 0xFF) / 4
        return (r shl 16) or (gg shl 8) or b
    }

    private companion object {
        const val MAGENTA = 0xFF00FF
    }
}
