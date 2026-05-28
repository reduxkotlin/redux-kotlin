package org.reduxkotlin.routing

import org.reduxkotlin.granular.subscribeTo
import kotlin.test.Test
import kotlin.test.assertEquals

class GranularIntegrationTest {

    @Test
    fun subscriber_fires_only_when_its_model_changes() {
        val s = createModelStore {
            model(UserModel()) {
                on<LoggedIn> { u, a -> u.copy(user = a.user) }
            }
            model(CartModel()) {
                on<AddItem> { c, a -> c.copy(items = c.items + a.item) }
            }
        }
        var userFires = 0
        s.subscribeTo(selector = { it.get<UserModel>() }, triggerOnSubscribe = false) { _, _ -> userFires++ }

        s.dispatch(AddItem("book")) // cart only -> user selector unchanged
        assertEquals(0, userFires)

        s.dispatch(LoggedIn("ann")) // user changes
        assertEquals(1, userFires)
    }
}
