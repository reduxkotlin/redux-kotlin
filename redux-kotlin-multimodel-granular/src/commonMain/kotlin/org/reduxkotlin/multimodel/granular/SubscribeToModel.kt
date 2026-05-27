package org.reduxkotlin.multimodel.granular

import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.granular.FieldSubscriptionScope
import org.reduxkotlin.granular.subscribeTo
import org.reduxkotlin.multimodel.ModelState
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.reflect.KProperty1

/**
 * Property-reference convenience for [ModelState]-shaped stores:
 * subscribe to a single field of model type [M], identified by a Kotlin
 * property reference like `LoggedInUserModel::displayName`.
 *
 * The model type [M] is inferred from the property reference's receiver
 * — the call site does not need to name [ModelState] at all. Internally
 * the selector is `state.get<M>().property`: two field reads instead of
 * one.
 *
 * Hidden from Swift via [HiddenFromObjC] (KProperty1 is not
 * constructible from Swift) and not `@JsExport`ed (inline + reified
 * cannot be exported). Raw JS/TS / Swift consumers should use
 * [subscribeToModel].
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public inline fun <reified M : Any, F> Store<ModelState>.subscribeTo(
    property: KProperty1<M, F>,
    triggerOnSubscribe: Boolean = true,
    noinline listener: (oldValue: F, newValue: F) -> Unit,
): StoreSubscription = subscribeTo(
    selector = { state -> property.get(state.get<M>()) },
    triggerOnSubscribe = triggerOnSubscribe,
    listener = listener,
)

/**
 * DSL counterpart of [subscribeTo] above, for use inside a
 * `subscribeFields { … }` block on a `Store<ModelState>`. Lets a single
 * batch of subscriptions mix fields from multiple feature models while
 * still backed by one underlying `store.subscribe`.
 *
 * Same Swift / JS visibility rules as the standalone overload.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public inline fun <reified M : Any, F> FieldSubscriptionScope<ModelState>.on(
    property: KProperty1<M, F>,
    triggerOnSubscribe: Boolean = true,
    noinline listener: (oldValue: F, newValue: F) -> Unit,
) {
    on(
        selector = { state -> property.get(state.get<M>()) },
        triggerOnSubscribe = triggerOnSubscribe,
        listener = listener,
    )
}
