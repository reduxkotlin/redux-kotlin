package org.reduxkotlin.devtools

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.createStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsEnhancerTest {

    private data class St(val n: Int = 0)
    private object Inc

    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @AfterTest
    fun cleanup() = DevToolsHub.reset()

    @Test
    fun enhancer_creates_a_session_and_does_not_alter_dispatch_result() {
        val store = createStore(reducer, St(), devTools(DevToolsConfig(name = "app")))
        store.dispatch(Inc)
        // Host behaviour is untouched.
        assertEquals(1, store.state.n)
        // A session exists in the hub for this store.
        assertEquals("app", DevToolsHub.sessions().single().id)
    }

    @Test
    fun enhancer_composes_with_applyMiddleware() {
        val log = mutableListOf<Any>()
        val mw: (org.reduxkotlin.Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ ->
                { next ->
                    { action ->
                        log.add(action)
                        next(action)
                    }
                }
            }
        val store = createStore(
            reducer,
            St(),
            org.reduxkotlin.compose(devTools(DevToolsConfig(name = "c")), applyMiddleware(mw)),
        )
        store.dispatch(Inc)
        assertEquals(1, store.state.n)
        assertEquals(listOf<Any>(Inc), log)
    }

    @Test
    fun throwing_serializer_never_breaks_dispatch_or_state() = runTest {
        val boom = object : ValueSerializer {
            override fun toJson(value: Any?): kotlinx.serialization.json.JsonElement =
                throw IllegalStateException("serializer boom")
        }
        val logged = mutableListOf<String>()
        val config = DevToolsConfig(name = "safe", serializer = boom, logger = { logged.add(it) })
        // Pre-register the session on the test dispatcher; the enhancer's createSession then
        // resolves it idempotently, so background processing drains deterministically.
        val dispatcher = StandardTestDispatcher(testScheduler)
        DevToolsHub.createSessionForTest(config, dispatcher)

        val store = createStore(reducer, St(), devTools(config))
        // Dispatch must succeed and the reducer's state must be correct even though the serializer
        // throws on the background coroutine. The host is never affected.
        store.dispatch(Inc)
        store.dispatch(Inc)

        assertEquals(2, store.state.n)

        // The failure is not silent: the session's consumer logs the serializer error.
        testScheduler.advanceUntilIdle()
        assertTrue(logged.any { it.contains("serializer boom") }, "expected the failure log, got: $logged")
    }
}
