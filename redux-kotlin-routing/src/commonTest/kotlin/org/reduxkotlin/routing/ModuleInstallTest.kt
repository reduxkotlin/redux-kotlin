package org.reduxkotlin.routing

import kotlin.test.Test
import kotlin.test.assertEquals

private data class Audit(val log: List<String> = emptyList())

class ModuleInstallTest {

    private val userModule = ReduxModule {
        model(UserModel()) {
            on<LoggedIn> { s, a -> s.copy(user = a.user) }
        }
    }

    @Test
    fun installed_module_contributes_models_and_handlers() {
        val s = createModelStore {
            install(userModule)
        }
        s.dispatch(LoggedIn("ann"))
        assertEquals("ann", s.state.get<UserModel>().user)
    }

    @Test
    fun handler_order_follows_install_order() {
        val first = ReduxModule {
            model(Audit()) {
                on<Checkout> { a, _ -> a.copy(log = a.log + "first") }
            }
        }
        val second = ReduxModule {
            onAction<Checkout> { reads, _ ->
                val a = reads.get<Audit>()
                writeSet { set(a.copy(log = a.log + "second")) }
            }
        }
        val s = createModelStore {
            install(first)
            install(second)
        }
        s.dispatch(Checkout)
        assertEquals(listOf("first", "second"), s.state.get<Audit>().log)
    }
}
