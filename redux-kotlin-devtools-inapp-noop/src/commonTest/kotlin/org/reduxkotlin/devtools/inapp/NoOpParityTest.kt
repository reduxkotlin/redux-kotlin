package org.reduxkotlin.devtools.inapp

import org.reduxkotlin.Store
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import kotlin.test.Test
import kotlin.test.assertEquals

class NoOpParityTest {

    private data class St(val n: Int = 0)
    private object Inc
    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @Test
    fun the_documented_integration_compiles_and_runs_inertly() {
        val cfg = DevToolsConfig(name = "release")
        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val root = devToolsCombineReducers(cfg, named("count", reducer))
        val store = createStore(root, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("logger", mw))))
        store.dispatch(Inc)
        ReduxDevTools.open()
        ReduxDevTools.close()
        assertEquals(1, store.state.n)
    }
}
