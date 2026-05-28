package org.reduxkotlin.concurrent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispatchContextTest {

    @Test
    fun inactive_by_default() {
        assertFalse(DispatchContext().isActive)
    }

    @Test
    fun active_between_enter_and_exit() {
        val ctx = DispatchContext()
        ctx.enter()
        assertTrue(ctx.isActive)
        ctx.exit()
        assertFalse(ctx.isActive)
    }

    @Test
    fun reentrant_depth_stays_active_until_balanced() {
        val ctx = DispatchContext()
        ctx.enter()
        ctx.enter()
        ctx.exit()
        assertTrue(ctx.isActive, "Still active after one of two exits (nested dispatch)")
        ctx.exit()
        assertFalse(ctx.isActive)
    }

    @Test
    fun two_instances_are_independent() {
        val a = DispatchContext()
        val b = DispatchContext()
        a.enter()
        assertTrue(a.isActive)
        assertFalse(b.isActive, "Entering one store's context must not activate another's")
        a.exit()
    }
}
