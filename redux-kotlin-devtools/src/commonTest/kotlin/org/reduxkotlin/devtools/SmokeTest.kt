package org.reduxkotlin.devtools

import kotlin.test.Test
import kotlin.test.assertTrue

class SmokeTest {
    @Test
    fun moduleCompiles() {
        assertTrue(systemClock() > 0L)
    }
}
