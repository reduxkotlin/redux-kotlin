package org.reduxkotlin.snapshot

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

internal class DashboardGeneratorTest {
    private fun tmp(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    @Test fun generates_self_contained_html_over_the_report() {
        val out = tmp("dash")
        val gdir = tmp("g")
        File(gdir, "good.png").writeBytes(demoSnapshots.render("counter", SnapshotInput.Preset("n3"), "dark").png)
        File(gdir, "bad.png").writeBytes(demoSnapshots.render("counter", SnapshotInput.Preset("n0"), "dark").png)
        val manifest = BatchManifest(
            shots = listOf(
                ShotSpec(id = "good", scene = "counter", preset = "n3"), // golden matches
                ShotSpec(id = "bad", scene = "counter", preset = "n3"), // golden is n0 -> mismatch
                ShotSpec(id = "err", scene = "nope", preset = "x"), // unknown scene -> error
            ),
        )
        val report = BatchRunner(demoSnapshots).run(manifest, out, verify = true, goldenDir = gdir, runId = "run-x")

        val index = DashboardGenerator.generate(report, out)
        assertTrue(index.isFile)
        val html = index.readText()

        assertTrue("run-x" in html, "runId missing")
        assertTrue("good" in html && "bad" in html && "err" in html, "shot ids missing")
        assertTrue("match" in html && "mismatch" in html && "error" in html, "status pills missing")
        assertTrue("""src="good.png"""" in html, "relative actual image ref missing")
        assertTrue(File(out, "goldens/good.png").isFile, "golden not copied for self-containment")
        assertTrue("""src="goldens/good.png"""" in html, "golden ref missing")
        // the mismatching shot gets a diff image written + shown in the triptych
        assertTrue(File(out, "bad.diff.png").isFile, "diff image not written for mismatch")
        assertTrue("""src="bad.diff.png"""" in html, "diff ref missing from dashboard")
        // click-to-zoom lightbox + smooth thumbnails (not nearest-neighbour pixelated)
        assertTrue("lbOpen(" in html && """id="lb"""" in html, "zoom lightbox missing")
        assertTrue("image-rendering:auto" in html, "thumbnails should downscale smoothly")
    }
}
