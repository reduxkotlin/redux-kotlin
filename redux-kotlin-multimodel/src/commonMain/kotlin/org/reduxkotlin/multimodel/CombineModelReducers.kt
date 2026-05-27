package org.reduxkotlin.multimodel

import org.reduxkotlin.Reducer
import kotlin.reflect.KClass

/**
 * A per-model reducer — same shape as a top-level [Reducer], but its
 * `state` parameter is the model instance itself rather than the
 * enclosing [ModelState].
 */
public typealias ModelReducer<M> = (model: M, action: Any) -> M

/**
 * The opaque pair produced by [modelReducer] and consumed by
 * [combineModelReducers]. The underlying reducer takes `Any` arguments
 * because [combineModelReducers] dispatches it heterogeneously across
 * all registered model slots — the type token in [modelClass] is what
 * makes the cast safe at registration time.
 */
public class ModelReducerEntry<M : Any> @PublishedApi internal constructor(
    public val modelClass: KClass<M>,
    @PublishedApi internal val reducer: ModelReducer<M>,
)

/**
 * Builds a [ModelReducerEntry] for model type [M]. The Kotlin-callable
 * sugar form; use [modelReducerOf] if you only have a [KClass] at the
 * call site (raw JS/TS, generic helpers).
 */
public inline fun <reified M : Any> modelReducer(
    noinline reducer: ModelReducer<M>,
): ModelReducerEntry<M> = ModelReducerEntry(M::class, reducer)

/**
 * Non-inline variant of [modelReducer] for callers that only hold a
 * [KClass] reference. Functionally equivalent.
 */
public fun <M : Any> modelReducerOf(
    modelClass: KClass<M>,
    reducer: ModelReducer<M>,
): ModelReducerEntry<M> = ModelReducerEntry(modelClass, reducer)

/**
 * Composes per-model reducers into a single [Reducer] over
 * [ModelState]. For each dispatch, every registered model reducer
 * receives the current instance of its model plus the action; if a
 * reducer returns the same instance (referential `===`), that slot is
 * left untouched and the resulting [ModelState] also retains its
 * existing instance — so the granular subscription layer's `===`
 * fast-path short-circuits without firing any listener for fields on
 * unchanged models.
 *
 * @throws IllegalArgumentException if two entries register reducers
 *   for the same model class (review I3 — silent last-wins chaining
 *   is a footgun, prefer to fail loudly).
 */
public fun combineModelReducers(
    vararg entries: ModelReducerEntry<*>,
): Reducer<ModelState> {
    val byClass = LinkedHashMap<KClass<*>, ModelReducer<Any>>(entries.size)
    for (entry in entries) {
        @Suppress("UNCHECKED_CAST")
        val erased = entry.reducer as ModelReducer<Any>
        require(byClass.put(entry.modelClass, erased) == null) {
            "Duplicate model reducer for ${entry.modelClass.simpleName ?: entry.modelClass}. " +
                "combineModelReducers requires at most one reducer per model class."
        }
    }
    return reducer@{ state, action ->
        var changedModels: MutableMap<KClass<*>, Any>? = null
        for ((klass, reducer) in byClass) {
            val oldModel = state.models[klass]
                ?: error(
                    "Reducer registered for ${klass.simpleName ?: klass} but no model of that type in ModelState. " +
                        "Every registered reducer must have a corresponding model in ModelState.of(...).",
                )
            val newModel = reducer(oldModel, action)
            if (newModel !== oldModel) {
                if (changedModels == null) {
                    changedModels = LinkedHashMap(state.models)
                }
                changedModels[klass] = newModel
            }
        }
        if (changedModels == null) state else ModelState(changedModels)
    }
}
