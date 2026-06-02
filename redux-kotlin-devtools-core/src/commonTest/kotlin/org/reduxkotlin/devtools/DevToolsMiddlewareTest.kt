package org.reduxkotlin.devtools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.Store
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsMiddlewareTest {

    private data class St(val n: Int = 0)
    private object Inc

    private val reducer: (St, Any) -> St = { s, a -> if (a is Inc) s.copy(n = s.n + 1) else s }

    @AfterTest fun cleanup() = DevToolsHub.reset()

    @Test
    fun wrapped_middleware_runs_in_order_and_returns_correct_state() {
        val log = mutableListOf<String>()
        val a: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ ->
                { next ->
                    { action ->
                        log.add("a")
                        next(action)
                    }
                }
            }
        val b: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ ->
                { next ->
                    { action ->
                        log.add("b")
                        next(action)
                    }
                }
            }

        val cfg = DevToolsConfig(name = "mw")
        val store = createStore(
            reducer,
            St(),
            compose(devTools(cfg), devToolsMiddleware(cfg, named("a", a), named("b", b))),
        )
        store.dispatch(Inc)

        assertEquals(1, store.state.n)
        assertEquals(listOf("a", "b"), log)
    }

    @Test
    fun structure_and_trace_are_emitted_for_a_dispatch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cfg = DevToolsConfig(name = "mw2")
        val session = DevToolsHub.createSessionForTest(cfg, dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }
        testScheduler.runCurrent()

        val mw: (Store<St>) -> ((Any) -> Any) -> (Any) -> Any =
            { _ -> { next -> { action -> next(action) } } }
        val store = createStore(reducer, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("logger", mw))))
        store.dispatch(Inc)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val structure = received.filterIsInstance<DevToolsEvent.PipelineRegistered>().single().structure
        assertTrue(structure.nodes.any { it.kind == PipelineNodeKind.MIDDLEWARE && it.label == "logger" })
        val traced = received.filterIsInstance<DevToolsEvent.PipelineTraced>().last()
        assertTrue(traced.trace.nodes.any { it.nodeId.contains("logger") })
    }
}
