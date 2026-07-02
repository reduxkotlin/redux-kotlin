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
        // list-argv overload: the string overload shell-tokenizes and eats the
        // backslashes in a Windows --out path, mangling it so no PNG is written.
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "counter", "--preset", "n3", "--out", out.path))
        assertEquals(0, r.statusCode, r.output)
        assertTrue(out.isFile && out.length() > 0)
    }

    @Test fun unknown_scene_exits_2() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "nope", "--preset", "n3", "--out", File(tmp, "x.png").path))
        assertEquals(2, r.statusCode)
    }

    @Test fun both_inputs_exits_2() {
        val r = snapshotCommand(demoSnapshots)
            .test(
                listOf("--scene", "counter", "--preset", "n3", "--state-json", "{}", "--out", File(tmp, "y.png").path),
            )
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

    @Test fun single_semantics_text_prints_dump() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "demo", "--preset", "default", "--semantics"))
        assertEquals(0, r.statusCode, r.output)
        assertTrue("node" in r.output, r.output)
    }

    @Test fun single_semantics_json_prints_json() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "demo", "--preset", "default", "--semantics", "--semantics-format", "json"))
        assertEquals(0, r.statusCode, r.output)
        assertTrue(r.output.trimStart().startsWith("["), r.output)
    }

    @Test fun update_semantics_then_verify_matches() {
        val golden = File(tmp, "demo.semantics.json")
        val g = snapshotCommand(demoSnapshots).test(
            listOf(
                "--scene",
                "demo",
                "--preset",
                "default",
                "--update-semantics",
                "--verify-semantics-file",
                golden.path,
            ),
        )
        assertEquals(0, g.statusCode, g.output)
        assertTrue(golden.isFile)
        val v = snapshotCommand(demoSnapshots).test(
            listOf("--scene", "demo", "--preset", "default", "--verify-semantics-file", golden.path),
        )
        assertEquals(0, v.statusCode, v.output)
        assertTrue("match" in v.output, v.output)
    }

    @Test fun verify_semantics_mismatch_exits_1() {
        val golden = File(tmp, "wrong.semantics.json").apply { writeText("[]") }
        val r = snapshotCommand(demoSnapshots).test(
            listOf("--scene", "demo", "--preset", "default", "--verify-semantics-file", golden.path),
        )
        assertEquals(1, r.statusCode, r.output)
        assertTrue("mismatch" in r.output, r.output)
    }

    @Test fun update_semantics_without_file_exits_2() {
        val r = snapshotCommand(demoSnapshots)
            .test(listOf("--scene", "demo", "--preset", "default", "--update-semantics"))
        assertEquals(2, r.statusCode, r.output)
    }

    @Test fun missing_verify_semantics_file_exits_2_with_fixit_message() {
        val golden = File(tmp, "absent.semantics.json")
        val r = snapshotCommand(demoSnapshots).test(
            listOf("--scene", "demo", "--preset", "default", "--verify-semantics-file", golden.path),
        )
        assertEquals(2, r.statusCode, r.output)
        assertTrue(golden.absolutePath in r.output, "should name the missing semantics golden path")
        assertTrue("--update-semantics" in r.output, "should tell the user how to generate it")
    }

    @Test fun single_semantics_with_out_writes_sidecar_files() {
        val outText = File(tmp, "shot.png")
        val rText = snapshotCommand(demoSnapshots).test(
            listOf("--scene", "demo", "--preset", "default", "--out", outText.path, "--semantics"),
        )
        assertEquals(0, rText.statusCode, rText.output)
        val sidecarText = File(tmp, "shot.png.semantics.txt")
        assertTrue(sidecarText.isFile, "expected sidecar at ${sidecarText.path}")
        assertTrue(sidecarText.length() > 0)

        val outJson = File(tmp, "shotj.png")
        val rJson = snapshotCommand(demoSnapshots).test(
            listOf(
                "--scene",
                "demo",
                "--preset",
                "default",
                "--out",
                outJson.path,
                "--semantics",
                "--semantics-format",
                "json",
            ),
        )
        assertEquals(0, rJson.statusCode, rJson.output)
        val sidecarJson = File(tmp, "shotj.png.semantics.json")
        assertTrue(sidecarJson.isFile, "expected sidecar at ${sidecarJson.path}")
        val jsonContent = sidecarJson.readText()
        assertTrue(jsonContent.isNotEmpty())
        assertTrue(jsonContent.trimStart().startsWith("["), jsonContent)
    }
}
