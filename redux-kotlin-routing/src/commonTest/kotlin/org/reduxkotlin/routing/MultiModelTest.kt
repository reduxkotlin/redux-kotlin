package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MultiModelTest {

    private fun store() = createModelStore {
        model(UserModel()) {
            on<LoggedIn> { s, a -> s.copy(user = a.user) }
        }
        model(CartModel(id = 7)) {
            on<AddItem> { s, a -> s.copy(items = s.items + a.item) }
        }
        onAction<Checkout> { reads, _ ->
            val cart = reads.get<CartModel>()
            writeSet {
                set(cart.copy(closed = true))
                set(reads.get<UserModel>().copy(lastOrder = cart.id))
            }
        }
    }

    @Test
    fun multi_model_handler_writes_several_models() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        s.dispatch(AddItem("book"))
        s.dispatch(Checkout)
        assertTrue(s.state.get<CartModel>().closed)
        assertEquals(7, s.state.get<UserModel>().lastOrder)
    }

    @Test
    fun later_handler_in_same_action_sees_earlier_write() {
        val s = createModelStore {
            model(CartModel(id = 1)) {
                on<Checkout> { c, _ -> c.copy(items = c.items + "auto") }
            }
            model(UserModel()) {}
            onAction<Checkout> { reads, _ ->
                val cart = reads.get<CartModel>() // must see the "auto" item
                writeSet { set(reads.get<UserModel>().copy(user = cart.items.joinToString())) }
            }
        }
        s.dispatch(Checkout)
        assertEquals("auto", s.state.get<UserModel>().user)
    }

    @Test
    fun unchanged_models_keep_identity_after_multi_write() {
        val s = store()
        s.dispatch(LoggedIn("ann"))
        val userBefore = s.state.get<UserModel>()
        s.dispatch(AddItem("book")) // touches only cart
        assertSame(userBefore, s.state.get<UserModel>())
    }
}
