package org.reduxkotlin.multimodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

private data class MsUser(val displayName: String = "", val isLoggedIn: Boolean = false)
private data class MsFeed(val items: List<String> = emptyList(), val isRefreshing: Boolean = false)
private data class MsUnregistered(val value: Int = 0)

class ModelStateTest {

    @Test
    fun get_returns_registered_model() {
        val state = ModelState.of(
            MsUser(displayName = "Ada"),
            MsFeed(items = listOf("a", "b")),
        )
        assertEquals(MsUser("Ada"), state.get<MsUser>())
        assertEquals(MsFeed(listOf("a", "b")), state.get<MsFeed>())
    }

    @Test
    fun get_throws_for_unregistered_class() {
        val state = ModelState.of(MsUser())
        val error = assertFailsWith<IllegalStateException> { state.get<MsUnregistered>() }
        // Message names the missing model so the call-site mistake is obvious.
        assertEquals(true, error.message!!.contains("MsUnregistered"))
    }

    @Test
    fun with_replaces_only_target_slot() {
        val initial = ModelState.of(MsUser("Ada"), MsFeed(listOf("a")))
        val next = initial.with(MsUser("Babbage"))

        assertEquals(MsUser("Babbage"), next.get<MsUser>())
        // Other slots share the same instance — verifies copy-on-write semantics.
        assertSame(initial.get<MsFeed>(), next.get<MsFeed>())
        assertNotSame(initial, next)
    }

    @Test
    fun with_throws_for_unregistered_class() {
        val state = ModelState.of(MsUser())
        assertFailsWith<IllegalStateException> { state.with(MsUnregistered(7)) }
    }

    @Test
    fun of_throws_on_duplicate_classes() {
        assertFailsWith<IllegalArgumentException> {
            ModelState.of(MsUser("a"), MsUser("b"))
        }
    }

    @Test
    fun non_reified_get_with_works_for_kclass_holders() {
        val state = ModelState.of(MsUser("Ada"))
        val klass = MsUser::class
        val fetched = state.get(klass)
        assertEquals(MsUser("Ada"), fetched)

        val next = state.with(klass, MsUser("Babbage"))
        assertEquals(MsUser("Babbage"), next.get<MsUser>())
    }

    @Test
    fun equality_is_value_based() {
        val a = ModelState.of(MsUser("Ada"), MsFeed(listOf("x")))
        val b = ModelState.of(MsUser("Ada"), MsFeed(listOf("x")))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
