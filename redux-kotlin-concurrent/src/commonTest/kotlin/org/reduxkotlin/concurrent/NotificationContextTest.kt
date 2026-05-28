package org.reduxkotlin.concurrent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationContextTest {

    @Test
    fun inline_runs_block_synchronously_on_caller() {
        val log = mutableListOf<String>()
        log.add("before")
        NotificationContext.Inline.post { log.add("during") }
        log.add("after")
        assertEquals(listOf("before", "during", "after"), log)
    }

    @Test
    fun custom_context_receives_the_block() {
        var captured: (() -> Unit)? = null
        val ctx = NotificationContext { block -> captured = block }
        var ran = false
        ctx.post { ran = true }
        assertTrue(!ran, "Custom context should not auto-run the block")
        captured?.invoke()
        assertTrue(ran, "Block should run when the custom context invokes it")
    }
}
