package org.reduxkotlin.devtools

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilteringTest {

    private object Increment
    private object Tick

    @Test
    fun empty_lists_allow_everything() {
        assertTrue(shouldSend(Increment, denylist = emptyList(), allowlist = emptyList()))
    }

    @Test
    fun non_empty_allowlist_only_admits_matching_actions() {
        val allow = listOf(Regex("Increment"))
        assertTrue(shouldSend(Increment, denylist = emptyList(), allowlist = allow))
        assertFalse(shouldSend(Tick, denylist = emptyList(), allowlist = allow))
    }

    @Test
    fun deny_wins_over_allow_when_both_match() {
        val allow = listOf(Regex("Increment"))
        val deny = listOf(Regex("Incr"))
        assertFalse(shouldSend(Increment, denylist = deny, allowlist = allow))
    }

    @Test
    fun denylist_alone_blocks_only_matching_actions() {
        val deny = listOf(Regex("Tick"))
        assertFalse(shouldSend(Tick, denylist = deny, allowlist = emptyList()))
        assertTrue(shouldSend(Increment, denylist = deny, allowlist = emptyList()))
    }
}
