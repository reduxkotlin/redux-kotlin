package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CreateModelStoreTest {

    private fun store() = createModelStore {
        model(UserModel()) {
            on<LoggedIn> { s, a -> s.copy(user = a.user) }
            on<LoggedOut> { s, _ -> s.copy(user = null) }
        }
        model(CartModel()) {
            on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
        }
    }

    @Test
    fun single_model_handler_updates_its_model() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        assertEquals("ann", s.state.get<UserModel>().user)
    }

    @Test
    fun action_routes_only_to_its_model() {
        val s = store()
        val before = s.state.get<CartModel>()
        s.dispatch(LoggedIn("ann"))
        assertSame(before, s.state.get<CartModel>())
    }

    @Test
    fun unhandled_action_leaves_state_identity_unchanged() {
        val s = store()
        val before = s.state
        s.dispatch(NeverHandled)
        assertSame(before, s.state)
    }

    @Test
    fun logged_out_clears_user() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        s.dispatch(LoggedOut)
        assertNull(s.state.get<UserModel>().user)
    }
}
