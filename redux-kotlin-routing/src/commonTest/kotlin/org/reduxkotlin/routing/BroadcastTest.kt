package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BroadcastTest {

    @Test
    fun broadcast_runs_for_every_installed_model() {
        val s = createModelStore {
            model(UserModel(user = "ann")) {}
            model(CartModel(items = listOf("book"))) {}
            onBroadcast<ResetAll> { model, _ ->
                when (model) {
                    is UserModel -> UserModel()
                    is CartModel -> CartModel()
                    else -> model
                }
            }
        }
        s.dispatch(ResetAll)
        assertNull(s.state.get<UserModel>().user)
        assertEquals(emptyList(), s.state.get<CartModel>().items)
    }

    @Test
    fun broadcast_covers_models_declared_after_it() {
        val s = createModelStore {
            model(UserModel(user = "ann")) {}
            onBroadcast<ResetAll> { model, _ -> if (model is UserModel) UserModel() else model }
            model(CartModel(items = listOf("book"))) {}
        }
        s.dispatch(ResetAll)
        assertNull(s.state.get<UserModel>().user)
    }
}
