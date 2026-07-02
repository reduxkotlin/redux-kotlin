package org.reduxkotlin.snapshot

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BatchRunnerTest {
    private fun tmp(prefix: String): File = Files.createTempDirectory(prefix).toFile()
    private fun newTmpDir(): File = File.createTempFile("rkbatch", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    @Test fun parses_manifest_json() {
        val m = Json.decodeFromString(
            BatchManifest.serializer(),
            """{"defaults":{"theme":"dark"},"shots":[{"id":"a","scene":"counter","preset":"n3"}]}""",
        )
        assertEquals(1, m.shots.size)
        assertEquals("counter", m.shots[0].scene)
        assertEquals("dark", m.defaults.theme)
    }

    @Test fun runs_shots_writes_images_and_isolates_failures() {
        val out = tmp("batch")
        val manifest = BatchManifest(
            shots = listOf(
                ShotSpec(id = "ok1", scene = "counter", preset = "n3"),
                ShotSpec(id = "ok2", scene = "counter", preset = "n0"),
                ShotSpec(id = "bad", scene = "nope", preset = "x"),
            ),
        )
        val report = BatchRunner(demoSnapshots).run(manifest, out, verify = false, goldenDir = null, runId = "t")
        assertEquals(3, report.totals.total)
        assertEquals(2, report.totals.ok)
        assertEquals(1, report.totals.failed)
        assertTrue(File(out, "ok1.png").isFile)
        assertTrue(File(out, "ok2.png").isFile)
        assertFalse(File(out, "bad.png").isFile, "failed shot must not write an image")
        assertEquals("error", report.shots.first { it.id == "bad" }.status)
        assertEquals(listOf(400, 400), report.shots.first { it.id == "ok1" }.sizePx)
    }

    @Test fun verify_reports_match_and_missing_golden() {
        val out = tmp("bo")
        val gdir = tmp("bg")
        File(gdir, "ok1.png").writeBytes(demoSnapshots.render("counter", SnapshotInput.Preset("n3"), "dark").png)
        val manifest = BatchManifest(
            shots = listOf(
                ShotSpec(id = "ok1", scene = "counter", preset = "n3"),
                ShotSpec(id = "absent", scene = "counter", preset = "n0"),
            ),
        )
        val report = BatchRunner(demoSnapshots).run(manifest, out, verify = true, goldenDir = gdir, runId = "t")
        assertEquals("match", report.shots.first { it.id == "ok1" }.verify?.result)
        assertEquals("missing-golden", report.shots.first { it.id == "absent" }.verify?.result)
    }

    @Test fun batch_writes_semantics_sidecars_when_requested() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val outDir = newTmpDir()
        val report = BatchRunner(demoSnapshots).run(
            manifest,
            outDir,
            verify = false,
            goldenDir = null,
            runId = "r",
            semantics = true,
        )
        val sidecar = File(outDir, "d.semantics.txt")
        assertTrue(sidecar.isFile, "sidecar missing")
        assertEquals(sidecar.path, report.shots.single().semanticsSidecar)
        assertTrue((report.shots.single().semanticsBytes ?: 0) > 0)
    }

    @Test fun semantics_verify_missing_golden_is_non_failing() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val goldenDir = newTmpDir() // empty
        val report = BatchRunner(demoSnapshots).run(
            manifest,
            newTmpDir(),
            verify = false,
            goldenDir = goldenDir,
            runId = "r",
            verifySemantics = true,
        )
        assertEquals("missing-golden", report.shots.single().verifySemantics?.result)
        assertEquals(0, report.totals.semanticsMismatched)
    }

    @Test fun update_semantics_writes_golden_then_verify_matches() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val goldenDir = newTmpDir()
        // 1) author baseline
        BatchRunner(demoSnapshots).run(
            manifest,
            newTmpDir(),
            verify = false,
            goldenDir = goldenDir,
            runId = "r",
            updateSemantics = true,
        )
        assertTrue(File(goldenDir, "d.semantics.json").isFile)
        // 2) verify against it
        val report = BatchRunner(demoSnapshots).run(
            manifest,
            newTmpDir(),
            verify = false,
            goldenDir = goldenDir,
            runId = "r2",
            verifySemantics = true,
        )
        assertEquals("match", report.shots.single().verifySemantics?.result)
        assertEquals(1, report.totals.semanticsMatched)
    }

    @Test fun totals_aggregate_render_ms() {
        val manifest = BatchManifest(shots = listOf(ShotSpec(id = "d", scene = "demo", preset = "default")))
        val report = BatchRunner(
            demoSnapshots,
        ).run(manifest, newTmpDir(), verify = false, goldenDir = null, runId = "r")
        assertTrue(report.totals.renderMsTotal >= 0)
    }
}
