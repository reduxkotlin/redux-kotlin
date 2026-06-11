package org.reduxkotlin.devtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.reduxkotlin.Store
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import org.reduxkotlin.devtools.inapp.ReduxDevTools
import kotlin.test.Test
import kotlin.test.assertEquals

class NoOpParityTest {

    private data class St(val n: Int = 0)
    private object Inc
    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @Test
    fun the_documented_integration_compiles_and_runs_inertly() {
        // Mirrors a REAL integrator's usage: core-package symbols from org.reduxkotlin.devtools,
        // in-app symbols from org.reduxkotlin.devtools.inapp. This must compile against the no-op
        // with NO core dependency on the (release) classpath.
        val redactor = object : ValueSerializer {
            override fun toJson(value: Any?) = JsonPrimitive("redacted")
        }
        val cfg = DevToolsConfig(name = "release", serializer = redactor)
        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val root = devToolsCombineReducers(cfg, named("count", reducer))
        val store = createStore(root, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("logger", mw))))
        store.dispatch(Inc)
        ReduxDevTools.open()
        ReduxDevTools.close()
        assertEquals(1, store.state.n)
    }

    @Test
    fun the_kotlinx_serializer_mirror_compiles_and_degrades_to_toString() {
        // The documented `DevToolsConfig(serializer = KotlinxValueSerializer(json))` pattern must
        // compile (and stay inert) against the release no-op artifact.
        val serializer = KotlinxValueSerializer(Json)
        val cfg = DevToolsConfig(name = "release-kotlinx", serializer = serializer)
        val store = createStore(reducer, St(), devTools(cfg))
        store.dispatch(Inc)
        assertEquals(1, store.state.n)
        assertEquals(JsonPrimitive("St(n=1)"), serializer.toJson(store.state))
    }
}
