package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RoutingGuardsTest {

    @Test
    fun registering_same_model_twice_throws() {
        assertFailsWith<IllegalArgumentException> {
            createModelStore {
                model(UserModel()) {}
                model(UserModel(user = "dup")) {}
            }
        }
    }

    @Test
    fun broadcast_transform_returning_same_instance_is_a_no_op() {
        val s = createModelStore {
            model(UserModel(user = "ann")) {}
            model(CartModel(items = listOf("book"))) {}
            // Transform returns each model unchanged -> no writes -> same ModelState.
            onBroadcast<ResetAll> { model, _ -> model }
        }
        val before = s.state
        s.dispatch(ResetAll)
        assertSame(before, s.state)
    }
}
