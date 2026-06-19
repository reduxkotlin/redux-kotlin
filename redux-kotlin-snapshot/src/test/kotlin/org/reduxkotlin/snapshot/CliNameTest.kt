package org.reduxkotlin.snapshot

import com.github.ajalt.clikt.testing.test
import org.reduxkotlin.snapshot.cli.snapshotCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CliNameTest {
    @Test fun command_is_named_snapshot() {
        assertEquals("snapshot", snapshotCommand(demoSnapshots).commandName)
    }

    @Test fun list_still_works() {
        val r = snapshotCommand(demoSnapshots).test("--list")
        assertTrue("scenes" in r.output)
    }
}
