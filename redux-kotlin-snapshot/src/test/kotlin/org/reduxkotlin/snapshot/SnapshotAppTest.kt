package org.reduxkotlin.snapshot

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class SnapshotAppTest {
    private val app = snapshotApp {
        defaults {
            width = 400
            height = 800
            density = 2f
            theme = "dark"
        }
        scene("demo") {
            presets("default")
            render { { Box(Modifier) } }
        }
    }

    @Test fun resolves_defaults_into_render_spec() {
        val r = app.resolve("demo", SnapshotInput.Preset("default"), null, null, null, null)
        assertEquals(400, r.widthDp)
        assertEquals(800, r.heightDp)
        assertEquals(2f, r.density)
        assertEquals("dark", r.theme)
    }

    @Test fun per_call_overrides_win() {
        val r = app.resolve("demo", SnapshotInput.Preset("default"), "light", 360, null, null)
        assertEquals(360, r.widthDp)
        assertEquals(800, r.heightDp)
        assertEquals("light", r.theme)
    }

    @Test fun unknown_scene_fails() {
        assertFailsWith<SnapshotException> {
            app.resolve("nope", SnapshotInput.Preset("default"), null, null, null, null)
        }
    }

    @Test fun lists_scenes_and_presets() {
        assertEquals(listOf("demo"), app.scenes.map { it.name })
        assertEquals(listOf("default"), app.scenes.first().presets)
    }
}
