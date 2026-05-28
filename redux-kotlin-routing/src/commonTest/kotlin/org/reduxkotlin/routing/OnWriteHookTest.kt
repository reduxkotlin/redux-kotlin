package org.reduxkotlin.routing

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class Write(val modelClass: KClass<*>, val prev: Any, val next: Any, val source: String)

class OnWriteHookTest {

    @Test
    fun hook_fires_once_per_effective_write_with_source() {
        val writes = mutableListOf<Write>()
        val s = createModelStore(
            onWrite = { _, modelClass, prev, next, source -> writes += Write(modelClass, prev, next, source) },
        ) {
            model(UserModel()) {
                on<LoggedIn> { u, a -> u.copy(user = a.user) }
            }
        }
        s.dispatch(LoggedIn("ann"))
        assertEquals(1, writes.size)
        assertEquals(UserModel::class, writes[0].modelClass)
        assertEquals(UserModel(), writes[0].prev)
        assertEquals(UserModel(user = "ann"), writes[0].next)
        assertTrue(writes[0].source.contains("UserModel"))
    }

    @Test
    fun hook_does_not_fire_for_no_op_handler() {
        val writes = mutableListOf<Write>()
        val s = createModelStore(
            onWrite = { _, modelClass, prev, next, source -> writes += Write(modelClass, prev, next, source) },
        ) {
            model(UserModel()) {
                on<LoggedOut> { u, _ -> u } // returns same instance -> no write
            }
        }
        s.dispatch(LoggedOut)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun hook_observes_intermediate_values_under_last_write_wins() {
        val nexts = mutableListOf<Any>()
        val s = createModelStore(
            onWrite = { _, _, _, next, _ -> nexts += next },
        ) {
            model(UserModel()) {
                on<Checkout> { u, _ -> u.copy(user = "a") }
            }
            onAction<Checkout> { reads, _ ->
                val next: UserModel = reads.get<UserModel>().copy(user = "b")
                writeSet { set(next) }
            }
        }
        s.dispatch(Checkout)
        assertEquals(
            listOf<Any>(UserModel(user = "a"), UserModel(user = "b")),
            nexts.toList(),
        )
        assertEquals("b", s.state.get<UserModel>().user) // committed = last write
    }
}
