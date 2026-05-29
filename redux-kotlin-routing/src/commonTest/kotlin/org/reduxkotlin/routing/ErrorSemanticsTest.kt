package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Boom : RuntimeException("boom")

class ErrorSemanticsTest {

    @Test
    fun throwing_handler_aborts_dispatch_without_partial_commit() {
        val s = createModelStore {
            model(UserModel()) {
                on<Checkout> { u, _ -> u.copy(user = "ann") } // staged first
            }
            onAction<Checkout> { _, _ -> throw Boom() } // throws before commit
        }
        val before = s.state
        assertFailsWith<Boom> { s.dispatch(Checkout) }
        // No partial commit: the earlier staged write must not survive.
        assertSame(before, s.state)
        assertTrue(s.state.get<UserModel>().user == null)
    }
}
