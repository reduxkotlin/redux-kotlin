package org.reduxkotlin.snapshot

import com.github.ajalt.clikt.testing.test
import org.reduxkotlin.snapshot.cli.snapshotCommand
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CliTest {
    private val tmp: File = File.createTempFile("rksnap", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    @Test fun list_emits_scene_names() {
        val r = snapshotCommand(demoSnapshots).test("--list")
        assertEquals(0, r.statusCode)
        assertTrue("counter" in r.output)
    }

    @Test fun single_render_writes_a_png() {
        val out = File(tmp, "counter.png")
        val r = snapshotCommand(demoSnapshots).test("--scene counter --preset n3 --out ${out.path}")
        assertEquals(0, r.statusCode, r.output)
        assertTrue(out.isFile && out.length() > 0)
    }

    @Test fun unknown_scene_exits_2() {
        val r = snapshotCommand(demoSnapshots).test("--scene nope --preset n3 --out ${File(tmp, "x.png").path}")
        assertEquals(2, r.statusCode)
    }

    @Test fun both_inputs_exits_2() {
        val r = snapshotCommand(
            demoSnapshots,
        ).test("--scene counter --preset n3 --state-json {} --out ${File(tmp, "y.png").path}")
        assertEquals(2, r.statusCode)
    }

    @Test fun state_json_render_writes_a_png() {
        val out = File(tmp, "cj.png")
        // list-argv overload avoids the string overload's shell tokenization stripping JSON quotes.
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "counter", "--state-json", """{"count":"2"}""", "--out", out.path))
        assertEquals(0, r.statusCode, r.output)
        assertTrue(out.isFile && out.length() > 0)
    }

    @Test fun malformed_state_json_exits_2() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "counter", "--state-json", "{bad", "--out", File(tmp, "z.png").path))
        assertEquals(2, r.statusCode)
    }

    @Test fun batch_writes_images_and_report() {
        val manifest = File(tmp, "shots.json").apply {
            writeText(
                """{"shots":[{"id":"a","scene":"counter","preset":"n3"},{"id":"b","scene":"counter","preset":"n0"}]}""",
            )
        }
        val outDir = File(tmp, "out")
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--batch", manifest.path, "--out-dir", outDir.path))
        assertEquals(0, r.statusCode, r.output)
        assertTrue(File(outDir, "a.png").isFile)
        assertTrue(File(outDir, "report.json").isFile)
    }

    @Test fun batch_with_failure_exits_1() {
        val manifest = File(tmp, "bad.json").apply {
            writeText("""{"shots":[{"id":"x","scene":"nope","preset":"n3"}]}""")
        }
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--batch", manifest.path, "--out-dir", File(tmp, "out2").path))
        assertEquals(1, r.statusCode)
    }

    @Test fun missing_batch_manifest_exits_2_with_fixit_message() {
        val missing = File(tmp, "nope.json")
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--batch", missing.path, "--out-dir", File(tmp, "o").path))
        assertEquals(2, r.statusCode, r.output)
        assertTrue(missing.absolutePath in r.output, "should name the resolved path it looked for")
        assertTrue("working dir" in r.output, "should explain relative-path resolution")
        assertTrue(""""shots"""" in r.output && "--list" in r.output, "should show a sample manifest + --list hint")
    }

    @Test fun missing_verify_golden_exits_2_with_fixit_message() {
        val golden = File(tmp, "absent-golden.png")
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "counter", "--preset", "n3", "--verify", golden.path))
        assertEquals(2, r.statusCode, r.output)
        assertTrue(golden.absolutePath in r.output, "should name the missing golden path")
        assertTrue("--out" in r.output, "should tell the user how to generate the golden")
    }

    @Test fun batch_dashboard_writes_index_html() {
        val manifest = File(tmp, "d.json").apply {
            writeText("""{"shots":[{"id":"a","scene":"counter","preset":"n3"}]}""")
        }
        val outDir = File(tmp, "dout")
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--batch", manifest.path, "--out-dir", outDir.path, "--dashboard"))
        assertEquals(0, r.statusCode, r.output)
        assertTrue(File(outDir, "index.html").isFile)
    }
}
