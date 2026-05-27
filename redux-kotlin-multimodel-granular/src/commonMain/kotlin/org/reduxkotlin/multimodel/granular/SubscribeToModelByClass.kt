package org.reduxkotlin.multimodel.granular

import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.granular.FieldSubscriptionScope
import org.reduxkotlin.granular.subscribeTo
import org.reduxkotlin.multimodel.ModelState
import kotlin.reflect.KClass

/**
 * Non-inline alternative to the reified [subscribeTo] overload, for
 * callers that hold the model type as a [KClass] rather than as a
 * compile-time generic — i.e. raw JS/TS consumers (`inline reified` is
 * Kotlin-only and is erased from the generated `.d.ts`), Swift
 * consumers, and generic helper code that re-dispatches across
 * dynamically chosen models.
 *
 * Functionally equivalent to:
 * ```
 * store.subscribeTo<M>(M::someProperty) { old, new -> … }
 * ```
 * but with the model class threaded through as a runtime argument:
 * ```
 * store.subscribeToModel(M::class, { it.someProperty }) { old, new -> … }
 * ```
 *
 * This is the resolution to adversarial-review item I11 — the inline
 * reified overload doesn't survive to the JS boundary, so a non-inline
 * pathway must exist for non-Kotlin consumers.
 */
public fun <M : Any, F> Store<ModelState>.subscribeToModel(
    modelClass: KClass<M>,
    selector: (M) -> F,
    triggerOnSubscribe: Boolean = true,
    listener: (oldValue: F, newValue: F) -> Unit,
): StoreSubscription = subscribeTo(
    selector = { state -> selector(state.get(modelClass)) },
    triggerOnSubscribe = triggerOnSubscribe,
    listener = listener,
)

/**
 * DSL counterpart of [subscribeToModel] for use inside a
 * `subscribeFields { … }` block on a `Store<ModelState>`.
 */
public fun <M : Any, F> FieldSubscriptionScope<ModelState>.onModel(
    modelClass: KClass<M>,
    selector: (M) -> F,
    triggerOnSubscribe: Boolean = true,
    listener: (oldValue: F, newValue: F) -> Unit,
) {
    on(
        selector = { state -> selector(state.get(modelClass)) },
        triggerOnSubscribe = triggerOnSubscribe,
        listener = listener,
    )
}
