package org.reduxkotlin.devtools

import org.reduxkotlin.Store
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineDegradeTest {

    private data class St(val n: Int = 0)
    private object Inc
    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @AfterTest fun cleanup() = DevToolsHub.reset()

    @Test
    fun plain_applyMiddleware_with_devTools_still_works_no_pipeline() {
        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val store = createStore(reducer, St(), compose(devTools(DevToolsConfig(name = "d")), applyMiddleware(mw)))
        store.dispatch(Inc)
        assertEquals(1, store.state.n)
    }

    @Test
    fun combinator_with_mismatched_config_id_is_transparent() {
        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val store = createStore(
            reducer,
            St(),
            compose(
                devTools(DevToolsConfig(name = "x")),
                devToolsMiddleware(DevToolsConfig(name = "y"), named("m", mw)),
            ),
        )
        store.dispatch(Inc)
        assertEquals(1, store.state.n)
    }
}
