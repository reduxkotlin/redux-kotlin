package org.reduxkotlin.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals

internal class RenderSpecTest {
    @Test fun px_is_dp_times_density_rounded() {
        val spec = RenderSpec(widthDp = 411, heightDp = 891, density = 2f) {}
        assertEquals(822, spec.widthPx)
        assertEquals(1782, spec.heightPx)
    }

    @Test fun density_rounds_half_up() {
        val spec = RenderSpec(widthDp = 10, heightDp = 10, density = 1.75f) {}
        assertEquals(18, spec.widthPx) // 17.5 -> 18
    }
}
