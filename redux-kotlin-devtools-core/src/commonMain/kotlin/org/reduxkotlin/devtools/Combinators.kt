package org.reduxkotlin.devtools

import org.reduxkotlin.Dispatcher
import org.reduxkotlin.Middleware
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.StoreCreator
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.compose
import kotlin.time.TimeSource

/** A [Middleware] paired with a display [label] for the pipeline view. Create with [named]. */
public class NamedMiddleware<State> internal constructor(
    internal val label: String,
    internal val middleware: Middleware<State>,
)

/** A whole-state [Reducer] paired with a display [label] for the pipeline view. Create with [named]. */
public class NamedReducer<State> internal constructor(internal val label: String, internal val reducer: Reducer<State>)

/** Labels a [middleware] for the pipeline view. */
public fun <State> named(label: String, middleware: Middleware<State>): NamedMiddleware<State> =
    NamedMiddleware(label, middleware)

/** Labels a whole-state [reducer] for the pipeline view. */
public fun <State> named(label: String, reducer: Reducer<State>): NamedReducer<State> = NamedReducer(label, reducer)

private fun mwNodeId(index: Int, label: String): String = "mw_${index}_$label"

private fun sliceNodeId(label: String): String = "slice_$label"

/**
 * A drop-in replacement for `applyMiddleware` that captures the pipeline. It owns the ordered
 * middleware list, registers the structure once, and per dispatch times each middleware and records
 * whether it forwarded the action (`next` called). Pass the **same** [config] used for [devTools];
 * it resolves (creating if needed, idempotently) the one session, so capture works regardless of
 * enhancer composition order. [config] is the shared DevTools config (keys the session); the
 * [middlewares] are the labeled middleware chain, applied left-to-right.
 */
public fun <State> devToolsMiddleware(
    config: DevToolsConfig,
    vararg middlewares: NamedMiddleware<State>,
): StoreEnhancer<State> = { storeCreator: StoreCreator<State> ->
    { reducer, initialState, enhancer ->
        val store: Store<State> = storeCreator(reducer, initialState, enhancer)
        val session = DevToolsHub.createSession(config)
        registerStructure(session, middlewares)

        val origDispatch = store.dispatch
        store.dispatch = {
            error(
                "Dispatching while constructing your middleware is not allowed. " +
                    "Other middleware would not be applied to this dispatch.",
            )
        }
        val chain: List<(Dispatcher) -> Dispatcher> = middlewares.mapIndexed { index, nm ->
            val real = nm.middleware(store)
            val nodeId = mwNodeId(index, nm.label)
            val link: (Dispatcher) -> Dispatcher = { next: Dispatcher ->
                { action: Any ->
                    var forwarded = false
                    val probeNext: Dispatcher = { a ->
                        forwarded = true
                        next(a)
                    }
                    val handler = real(probeNext)
                    val mark = TimeSource.Monotonic.markNow()
                    val result = handler(action)
                    session.pipeline.node(nodeId, mark.elapsedNow().inWholeNanoseconds, forwarded, changed = false)
                    result
                }
            }
            link
        }
        store.dispatch = bracketed(session, compose(chain)(origDispatch))
        store
    }
}

private fun <State> registerStructure(session: DevToolsSession, middlewares: Array<out NamedMiddleware<State>>) {
    val nodes = ArrayList<PipelineNode>()
    nodes.add(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY))
    middlewares.forEachIndexed { i, nm ->
        nodes.add(PipelineNode(mwNodeId(i, nm.label), nm.label, PipelineNodeKind.MIDDLEWARE))
    }
    nodes.add(PipelineNode("rootReducer", "rootReducer", PipelineNodeKind.REDUCER))
    session.registerPipeline(PipelineStructure(nodes))
}

private fun bracketed(session: DevToolsSession, composed: Dispatcher): Dispatcher = { action ->
    session.pipeline.begin()
    try {
        composed(action)
    } finally {
        session.pipeline.commit(actionId = -1)?.let { session.submitTrace(it.nodes) }
    }
}

/**
 * A drop-in replacement for `combineReducers` that captures the reducer pipeline. redux-kotlin folds
 * whole-state reducers in order; this records each as a `SLICE` node, times it, and flags
 * `changed = output !== input`. Pass the **same** [config] used for [devTools]; without a session the
 * reducers run transparently. If [devToolsMiddleware] is not used, this self-brackets the trace.
 * [config] is the shared DevTools config (keys the session); the [reducers] are the labeled
 * whole-state reducers, folded left-to-right.
 */
public fun <State> devToolsCombineReducers(
    config: DevToolsConfig,
    vararg reducers: NamedReducer<State>,
): Reducer<State> {
    val id = config.instanceId ?: config.name
    return { state, action ->
        val session = DevToolsHub.session(id)
        if (session != null) {
            maybeRegisterSlices(session, reducers)
            val selfBracket = !session.pipeline.isActive
            if (selfBracket) session.pipeline.begin()
            val next = reducers.fold(state) { acc, nr ->
                val mark = TimeSource.Monotonic.markNow()
                val out = nr.reducer(acc, action)
                session.pipeline.node(
                    sliceNodeId(nr.label),
                    mark.elapsedNow().inWholeNanoseconds,
                    forwarded = true,
                    changed = out !== acc,
                )
                out
            }
            if (selfBracket) session.pipeline.commit(actionId = -1)?.let { session.submitTrace(it.nodes) }
            next
        } else {
            reducers.fold(state) { acc, nr -> nr.reducer(acc, action) }
        }
    }
}

private fun <State> maybeRegisterSlices(session: DevToolsSession, reducers: Array<out NamedReducer<State>>) {
    val nodes = ArrayList<PipelineNode>()
    nodes.add(PipelineNode("dispatch", "dispatch(action)", PipelineNodeKind.ENTRY))
    nodes.add(PipelineNode("rootReducer", "rootReducer", PipelineNodeKind.REDUCER))
    reducers.forEach { nodes.add(PipelineNode(sliceNodeId(it.label), it.label, PipelineNodeKind.SLICE)) }
    session.registerPipeline(PipelineStructure(nodes))
}
