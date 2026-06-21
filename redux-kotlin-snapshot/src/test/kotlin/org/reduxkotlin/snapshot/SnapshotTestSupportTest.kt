package org.reduxkotlin.snapshot

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class SnapshotTestSupportTest {
    @Test fun record_then_verify_passes() {
        val dir = Files.createTempDirectory("rec").toFile()
        demoSnapshots.assertGolden("counter", preset = "n3", goldenDir = dir, name = "c", record = true)
        assertTrue(dir.resolve("c.png").isFile, "record did not write the golden")
        // verify against the just-recorded golden
        demoSnapshots.assertGolden(
            "counter",
            preset = "n3",
            goldenDir = dir,
            name = "c",
            tolerance = 4,
            maxDiffPercent = 0.5,
        )
    }

    @Test fun missing_golden_throws() {
        val dir = Files.createTempDirectory("miss").toFile()
        assertFailsWith<AssertionError> {
            demoSnapshots.assertGolden("counter", preset = "n3", goldenDir = dir, name = "absent")
        }
    }

    @Test fun mismatch_throws() {
        val dir = Files.createTempDirectory("mis").toFile()
        demoSnapshots.assertGolden("counter", preset = "n0", goldenDir = dir, name = "m", record = true)
        assertFailsWith<AssertionError> {
            demoSnapshots.assertGolden(
                "counter",
                preset = "n3",
                goldenDir = dir,
                name = "m",
                tolerance = 4,
                maxDiffPercent = 0.5,
            )
        }
    }
}
