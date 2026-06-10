package org.reduxkotlin.routing

import org.reduxkotlin.Store
import org.reduxkotlin.StoreEnhancer
import org.reduxkotlin.createStore
import org.reduxkotlin.multimodel.ModelState
import kotlin.reflect.KClass

/**
 * Observer invoked for every effective model write during a dispatch,
 * i.e. only when the new instance is referentially different
 * (`next !== prev`). Fired synchronously during the fold, before the
 * dispatch commits, so under last-write-wins it can observe
 * intermediate (clobbered) values. It MUST be a pure observer: it must
 * not call `dispatch` or read the store.
 */
public typealias OnWrite = (action: Any, modelClass: KClass<*>, prev: Any, next: Any, source: String) -> Unit

/**
 * Builds a [Store] of [ModelState] from a routing [block]. Each model's
 * starting value comes from its [RoutingBuilder.model] declaration;
 * there is no INIT-action fan-out. Dispatch routes by the exact leaf
 * class of the action.
 *
 * @param enhancer optional store enhancer (e.g. `applyMiddleware`),
 *   passed straight to [createStore].
 * @param devChecks when true, throws if a handler returns a new but
 *   structurally-equal model instance (a wasteful no-op write that
 *   would fire subscribers spuriously).
 * @param onWrite optional [OnWrite] observer for tracing/conflict
 *   detection.
 * @param preloadedState optional restored/persisted models overlaid onto
 *   the declared defaults at construction (its key set must be a subset
 *   of the declared models). Use to rehydrate state synchronously instead
 *   of dispatching after first render.
 * @param block the routing configuration: model and handler
 *   registrations applied to a [RoutingBuilder].
 */
public fun createModelStore(
    enhancer: StoreEnhancer<ModelState>? = null,
    devChecks: Boolean = false,
    onWrite: OnWrite? = null,
    preloadedState: ModelState? = null,
    block: RoutingBuilder.() -> Unit,
): Store<ModelState> {
    val builder = RoutingBuilder()
    builder.block()
    val declared = builder.buildInitialState()
    val initialState = if (preloadedState == null) declared else declared.withAll(preloadedState)
    val reducer = builder.buildReducer(devChecks, onWrite)
    return createStore(reducer, initialState, enhancer)
}
