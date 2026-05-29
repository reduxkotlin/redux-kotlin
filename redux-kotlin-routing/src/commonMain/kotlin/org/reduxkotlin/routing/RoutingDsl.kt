package org.reduxkotlin.routing

import org.reduxkotlin.Reducer
import org.reduxkotlin.multimodel.ModelState
import kotlin.reflect.KClass

/**
 * Collects model initial instances and action handlers, then produces
 * the initial [ModelState] and the routed [Reducer]. This is the
 * receiver of the [createModelStore] lambda and of [ReduxModule]
 * contributions. Registration order across the whole block (including
 * [install]ed modules) fixes the dispatch order for any action handled
 * by more than one handler.
 */
public class RoutingBuilder @PublishedApi internal constructor() {

    @PublishedApi
    internal val initials: MutableList<Any> = mutableListOf()

    @PublishedApi
    internal val modelClasses: MutableList<KClass<*>> = mutableListOf()

    @PublishedApi
    internal val table: MutableMap<Any, MutableList<Cell>> = LinkedHashMap()

    @PublishedApi
    internal val broadcasts: MutableList<Broadcast> = mutableListOf()

    /**
     * Registers a model of type [M] with its [initial] instance and its
     * single-model handlers. The initial instance is the sole source of
     * that model's starting value — there is no INIT action fan-out.
     *
     * @throws IllegalArgumentException if [M] is registered more than
     *   once.
     */
    public inline fun <reified M : Any> model(initial: M, block: ModelHandlerScope<M>.() -> Unit) {
        registerModel(M::class, initial)
        ModelHandlerScope(M::class, this).block()
    }

    /**
     * Registers a multi-model handler for action [A]. The handler reads
     * any models via [Reads] and returns a [WriteSet] of replacements.
     * The handler must be pure: it must not call `dispatch` or read the
     * store, only compute the next models from its inputs.
     */
    public inline fun <reified A : Any> onAction(noinline handler: (reads: Reads, action: A) -> WriteSet) {
        addCell(A::class, "onAction:${A::class.simpleName}") { working, action ->
            @Suppress("UNCHECKED_CAST")
            handler(working, action as A).changes
        }
    }

    /**
     * Registers a broadcast handler for action [A] that runs once per
     * installed model. [transform] receives each model instance and
     * returns its (possibly unchanged) replacement. Use for
     * cross-cutting actions such as reset/logout that every model must
     * observe. The transform must be pure: it must not call `dispatch`
     * or read the store, only compute the next model from its inputs.
     */
    public inline fun <reified A : Any> onBroadcast(noinline transform: (model: Any, action: A) -> Any) {
        @Suppress("UNCHECKED_CAST")
        registerBroadcast(A::class) { model, action -> transform(model, action as A) }
    }

    @PublishedApi
    internal fun registerModel(modelClass: KClass<*>, initial: Any) {
        require(modelClasses.none { it == modelClass }) {
            "Model ${modelClass.simpleName ?: modelClass} registered more than once in createModelStore { }."
        }
        modelClasses += modelClass
        initials += initial
    }

    @PublishedApi
    internal fun addCell(actionClass: KClass<*>, source: String, handler: (Reads, Any) -> Map<KClass<*>, Any>) {
        table.getOrPut(actionClass) { mutableListOf() } += Cell(source) { working, action -> handler(working, action) }
    }

    @PublishedApi
    internal fun registerBroadcast(actionClass: KClass<*>, perModel: (Any, Any) -> Any) {
        broadcasts += Broadcast(actionClass, perModel)
    }

    internal fun buildInitialState(): ModelState = ModelState.of(*initials.toTypedArray())

    internal fun buildReducer(devChecks: Boolean, onWrite: OnWrite?): Reducer<ModelState> =
        routedReducer(table, broadcasts, devChecks, onWrite, modelClasses.toList())
}

/**
 * The receiver of a [RoutingBuilder.model] block; registers
 * single-model handlers for model type [M].
 */
public class ModelHandlerScope<M : Any> @PublishedApi internal constructor(
    @PublishedApi internal val modelClass: KClass<M>,
    @PublishedApi internal val builder: RoutingBuilder,
) {
    /**
     * Registers a pure single-model handler for action [A]: given the
     * current model and the action, return the next model instance
     * (return the same instance to signal "no change"). Matching is by
     * exact leaf class — a handler on [A] does not catch subtypes of
     * [A]. The reducer must be pure: it must not call `dispatch` or read
     * the store, only compute the next model from its inputs.
     */
    public inline fun <reified A : Any> on(noinline reducer: (model: M, action: A) -> M) {
        builder.addCell(A::class, "model:${modelClass.simpleName}") { working, action ->
            val model = working.get(modelClass)

            @Suppress("UNCHECKED_CAST")
            val next = reducer(model, action as A)
            if (next !== model) mapOf<KClass<*>, Any>(modelClass to next) else emptyMap()
        }
    }
}

/**
 * A reusable bundle of model and handler registrations that can be
 * [install]ed into a [RoutingBuilder]. Lets feature modules package
 * their slice of the store; the app fixes ordering by the sequence of
 * [install] calls.
 */
public fun interface ReduxModule {
    /** Contributes this module's models and handlers to the builder. */
    public fun RoutingBuilder.contribute()
}

/**
 * Applies a [ReduxModule]'s registrations to this builder, in call
 * order.
 */
public fun RoutingBuilder.install(module: ReduxModule) {
    with(module) { contribute() }
}
