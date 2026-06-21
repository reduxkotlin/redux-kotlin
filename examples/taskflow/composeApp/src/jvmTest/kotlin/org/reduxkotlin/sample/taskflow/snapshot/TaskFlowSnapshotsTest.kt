package org.reduxkotlin.sample.taskflow.snapshot

import org.reduxkotlin.snapshot.SnapshotInput
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke tests that real TaskFlow screens render headlessly from seeded state via the snapshot
 * library. No committed goldens — these screens are text-heavy, so cross-OS golden stability waits
 * on the bundled-font determinism harness; here we only prove the consumer wiring + that each
 * screen renders a non-trivial frame at the expected size.
 */
internal class TaskFlowSnapshotsTest {
    private fun render(scene: String, preset: String): ByteArray =
        taskFlowSnapshots.render(scene, SnapshotInput.Preset(preset)).png

    @Test fun board_seeded_renders_a_full_frame() {
        val png = render("board", "seeded")
        assertTrue(png.size > 2_000, "board render too small: ${png.size}")
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(822, img.width) // 411dp * 2
        assertEquals(1782, img.height)
    }

    @Test fun settings_renders() {
        assertTrue(render("settings", "default").size > 2_000)
        assertTrue(render("settings", "offline-failing").size > 2_000)
    }
}
