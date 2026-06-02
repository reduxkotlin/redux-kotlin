package org.reduxkotlin.devtools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.compose
import org.reduxkotlin.createStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevToolsCombineReducersTest {

    private data class St(val todos: Int = 0, val filter: String = "ALL")
    private object AddTodo
    private data class SetFilter(val f: String)

    private val todos: (St, Any) -> St = { s, a -> if (a is AddTodo) s.copy(todos = s.todos + 1) else s }
    private val filter: (St, Any) -> St = { s, a -> if (a is SetFilter) s.copy(filter = a.f) else s }

    @AfterTest fun cleanup() = DevToolsHub.reset()

    @Test
    fun combined_reducer_folds_named_reducers_in_order() {
        val root = devToolsCombineReducers(DevToolsConfig(name = "r"), named("todos", todos), named("filter", filter))
        val store = createStore(root, St(), devTools(DevToolsConfig(name = "r")))
        store.dispatch(AddTodo)
        store.dispatch(SetFilter("DONE"))
        assertEquals(St(todos = 1, filter = "DONE"), store.state)
    }

    @Test
    fun slice_changed_flag_reflects_which_reducer_produced_new_state() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cfg = DevToolsConfig(name = "r2")
        val session = DevToolsHub.createSessionForTest(cfg, dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }
        testScheduler.runCurrent()

        val root = devToolsCombineReducers(cfg, named("todos", todos), named("filter", filter))
        val store = createStore(root, St(), compose(devTools(cfg), devToolsMiddleware(cfg)))
        store.dispatch(AddTodo)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val traced = received.filterIsInstance<DevToolsEvent.PipelineTraced>().last()
        val todosNode = traced.trace.nodes.first { it.nodeId.contains("todos") }
        val filterNode = traced.trace.nodes.first { it.nodeId.contains("filter") }
        assertTrue(todosNode.changed)
        assertTrue(!filterNode.changed)
    }

    @Test
    fun both_combinators_yield_one_structure_with_all_node_kinds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cfg = DevToolsConfig(name = "both")
        val session = DevToolsHub.createSessionForTest(cfg, dispatcher)
        val received = mutableListOf<DevToolsEvent>()
        val job = launch(dispatcher) { session.events.toList(received) }
        testScheduler.runCurrent()

        val mw: (org.reduxkotlin.Store<St>) -> ((Any) -> Any) -> (Any) -> Any = { _ -> { next -> { a -> next(a) } } }
        val root = devToolsCombineReducers(cfg, named("todos", todos), named("filter", filter))
        val store = createStore(root, St(), compose(devTools(cfg), devToolsMiddleware(cfg, named("logger", mw))))
        store.dispatch(AddTodo)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val kinds = received.filterIsInstance<DevToolsEvent.PipelineRegistered>().last().structure.nodes.map {
            it.kind
        }.toSet()
        assertEquals(
            setOf(
                PipelineNodeKind.ENTRY,
                PipelineNodeKind.MIDDLEWARE,
                PipelineNodeKind.REDUCER,
                PipelineNodeKind.SLICE,
            ),
            kinds,
        )
    }
}
