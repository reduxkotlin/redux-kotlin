package org.reduxkotlin.multimodel

import kotlin.reflect.KClass

/**
 * An immutable container that holds a fixed set of typed feature models.
 *
 * Each model is keyed by its concrete [KClass]; the *set* of model
 * classes is locked at construction time via [Companion.of] and cannot
 * be expanded or contracted. The *instances* can be swapped through
 * [with], which produces a new [ModelState] sharing the same key set.
 *
 * This shape exists to support feature-modular state: every screen or
 * subsystem owns its own model type, and the root reducer composes
 * per-model reducers via [combineModelReducers]. Granular subscribers
 * (see `redux-kotlin-multimodel-granular`) can subscribe to a single
 * field on a single model without the rest of the app's state passing
 * through their selector.
 *
 * **Why a sealed key set.** The library guarantees that [get] always
 * returns a non-null model: every model class declared at construction
 * is present at all times. Reducers can replace an instance but never
 * remove the slot. Asking for a model class that was never registered
 * is a programming error and throws [IllegalStateException], not a
 * runtime branch the caller must handle. This is the resolution to
 * adversarial-review blocker B1 — the common selector path never
 * confronts a missing model.
 *
 * Each model is responsible for its own "not yet loaded" semantics —
 * supply a default constructor or a public `NOT_SET` sentinel
 * instance, so dependent code reads `String` rather than `String?`.
 */
public class ModelState @PublishedApi internal constructor(@PublishedApi internal val models: Map<KClass<*>, Any>) {

    /**
     * Returns the registered model of type [M].
     *
     * @throws IllegalStateException if [M] was not declared in the
     *   [Companion.of] call that produced this [ModelState]. This is a
     *   programming error; the set of model classes is fixed at
     *   construction.
     */
    public inline fun <reified M : Any> get(): M = get(M::class)

    /**
     * Non-reified overload of [get] for callers that hold the model's
     * [KClass] in a variable — e.g. raw JS/TS consumers that cannot use
     * reified type parameters, or generic helper code.
     *
     * @throws IllegalStateException if [modelClass] was not declared at
     *   construction.
     */
    public fun <M : Any> get(modelClass: KClass<M>): M {
        val model = models[modelClass]
            ?: error(
                "Model ${modelClass.simpleName ?: modelClass} not registered in ModelState. " +
                    "Every model must be declared in ModelState.of(...).",
            )
        @Suppress("UNCHECKED_CAST")
        return model as M
    }

    /**
     * Returns a new [ModelState] with the existing slot for [M]
     * replaced by [model]. Other models are shared with the receiver.
     *
     * @throws IllegalStateException if [M] was not declared at
     *   construction; new model classes cannot be introduced after the
     *   initial [Companion.of] call.
     */
    public inline fun <reified M : Any> with(model: M): ModelState = with(M::class, model)

    /**
     * Non-reified overload of [with] for callers that hold the model's
     * [KClass] in a variable. The runtime type of [model] must be
     * assignable to [modelClass]; otherwise downstream `get<M>()` calls
     * will throw `ClassCastException`.
     */
    public fun <M : Any> with(modelClass: KClass<M>, model: M): ModelState {
        check(modelClass in models) {
            "Cannot replace model ${modelClass.simpleName ?: modelClass} that wasn't declared at construction. " +
                "ModelState's key set is fixed by ModelState.of(...)."
        }
        return ModelState(models + (modelClass to model))
    }

    override fun equals(other: Any?): Boolean = other is ModelState && other.models == models

    override fun hashCode(): Int = models.hashCode()

    override fun toString(): String = "ModelState(${models.entries.joinToString { (k, v) -> "${k.simpleName}=$v" }})"

    /** Factory methods for [ModelState]; the only public entry point. */
    public companion object {
        /**
         * Builds a [ModelState] containing exactly the supplied models.
         * The set of model *classes* — one entry per distinct
         * `model::class` — is locked for the lifetime of every
         * [ModelState] derived from this one.
         *
         * @throws IllegalArgumentException if any two models share the
         *   same concrete class; each model class may appear at most
         *   once in a [ModelState].
         */
        public fun of(vararg models: Any): ModelState {
            val map = LinkedHashMap<KClass<*>, Any>(models.size)
            for (model in models) {
                val klass = model::class
                require(map.put(klass, model) == null) {
                    "Duplicate model class ${klass.simpleName ?: klass} passed to ModelState.of(...). " +
                        "Each model class may appear at most once."
                }
            }
            return ModelState(map)
        }
    }
}
