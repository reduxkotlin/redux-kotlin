package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private sealed interface Nav
private data class Open(val screen: String) : Nav
private data class Close(val screen: String) : Nav

private data class NavModel(val current: String = "home", val opens: Int = 0)

class ExactLeafMatchingTest {

    private fun store() = createModelStore {
        model(NavModel()) {
            on<Open> { s, a -> s.copy(current = a.screen, opens = s.opens + 1) }
        }
    }

    @Test
    fun handler_matches_its_exact_leaf_class() {
        val s = store()
        s.dispatch(Open("profile"))
        assertEquals("profile", s.state.get<NavModel>().current)
        assertEquals(1, s.state.get<NavModel>().opens)
    }

    @Test
    fun handler_does_not_match_a_sibling_subtype() {
        val s = store()
        val before = s.state
        s.dispatch(Close("profile")) // no handler registered for Close
        assertSame(before, s.state)
    }
}
