package org.reduxkotlin.snapshot

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs

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
}
