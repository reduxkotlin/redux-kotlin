package org.reduxkotlin.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RenderBackendTest {
    @Test fun renders_a_red_box_to_a_png_of_the_right_size() {
        val result = ImageComposeSceneBackend().render(
            RenderSpec(widthDp = 100, heightDp = 50, density = 2f) {
                Box(Modifier.fillMaxSize().background(Color.Red))
            },
        )
        assertTrue(result.png.isNotEmpty(), "png bytes empty")
        val img = ImageIO.read(ByteArrayInputStream(result.png))
        assertEquals(200, img.width)
        assertEquals(100, img.height)
        assertEquals(0xFF0000, img.getRGB(100, 50) and 0xFFFFFF) // center pixel red
    }
}
