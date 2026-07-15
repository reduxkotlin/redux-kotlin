package org.reduxkotlin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.granular.SelectorSubscriptions
import org.reduxkotlin.granular.subscribeTo
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.reflect.KProperty1

/**
 * Returns a Compose [State] that always reflects `selector(store.state)`.
 *
 * The value is read synchronously from the store on every [State.value]
 * access. Its subscription only schedules recomposition when the selected
 * value changes, so it remains current even when a concurrent store posts
 * notifications asynchronously. The selector is retained for the lifetime of
 * this store binding; use [selectorState] with an explicit key when it closes
 * over a parameter that can change.
 *
 * For expensive derived values, pass a stable
 * `org.reduxkotlin.granular.memoizedSelector` instance. For several sibling
 * bindings, use [rememberSelectorSubscriptions] and the scoped overload to
 * share one underlying store callback.
 */
@Composable
public fun <S, F> Store<S>.selectorState(selector: (S) -> F): State<F> = selectorStateBinding(
    selectorKey = Unit,
    selector = selector,
    subscriptionKey = Unit,
) { stableSelector, listener ->
    subscribeTo(stableSelector, triggerOnSubscribe = false, listener = listener)
}

/**
 * Returns a Compose [State] for [selector], replacing its retained selector
 * whenever [key] changes. Use this overload for a selector that closes over a
 * changing id, filter, or other Compose value.
 */
@Composable
public fun <S, F> Store<S>.selectorState(key: Any?, selector: (S) -> F): State<F> = selectorStateBinding(
    selectorKey = key,
    selector = selector,
    subscriptionKey = Unit,
) { stableSelector, listener ->
    subscribeTo(stableSelector, triggerOnSubscribe = false, listener = listener)
}

/**
 * Returns a Compose [State] backed by [subscriptions], letting sibling
 * bindings share one underlying store callback. Obtain [subscriptions] from
 * this store with [rememberSelectorSubscriptions] and hoist it to the common
 * screen or subtree that owns the bindings.
 */
@Composable
public fun <S, F> Store<S>.selectorState(subscriptions: SelectorSubscriptions<S>, selector: (S) -> F): State<F> =
    selectorStateBinding(
        selectorKey = Unit,
        selector = selector,
        subscriptionKey = subscriptions,
    ) { stableSelector, listener ->
        subscriptions.subscribeTo(stableSelector, triggerOnSubscribe = false, listener = listener)
    }

/**
 * Scoped counterpart of [selectorState] that replaces the retained selector
 * whenever [key] changes while keeping the binding in [subscriptions].
 */
@Composable
public fun <S, F> Store<S>.selectorState(
    subscriptions: SelectorSubscriptions<S>,
    key: Any?,
    selector: (S) -> F,
): State<F> = selectorStateBinding(
    selectorKey = key,
    selector = selector,
    subscriptionKey = subscriptions,
) { stableSelector, listener ->
    subscriptions.subscribeTo(stableSelector, triggerOnSubscribe = false, listener = listener)
}

/**
 * Returns a Compose [State] for [selector] through this facade's shared
 * subscription group. Use [rememberSelectorStore] once at the composition
 * root, then pass the resulting [SelectorStore] to binding components.
 */
@Composable
public fun <S, F> SelectorStore<S>.selectorState(selector: (S) -> F): State<F> =
    (this as Store<S>).selectorState(subscriptions, selector)

/**
 * Shared-facade counterpart of [selectorState] that replaces the retained
 * selector whenever [key] changes. Use it when [selector] captures a
 * changing id, filter, or other Compose value.
 */
@Composable
public fun <S, F> SelectorStore<S>.selectorState(key: Any?, selector: (S) -> F): State<F> =
    (this as Store<S>).selectorState(subscriptions, key, selector)

/**
 * Property-reference convenience for [selectorState]. The property reference
 * is stable, so it does not need an explicit key.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public fun <S, F> Store<S>.fieldState(property: KProperty1<S, F>): State<F> = selectorStateBinding(
    selectorKey = property,
    selector = property::get,
    subscriptionKey = Unit,
) { stableSelector, listener ->
    subscribeTo(stableSelector, triggerOnSubscribe = false, listener = listener)
}

/**
 * Property-reference convenience for a shared [subscriptions] scope. The
 * scope must have been created for this store.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public fun <S, F> Store<S>.fieldState(subscriptions: SelectorSubscriptions<S>, property: KProperty1<S, F>): State<F> =
    selectorStateBinding(
        selectorKey = property,
        selector = property::get,
        subscriptionKey = subscriptions,
    ) { stableSelector, listener ->
        subscriptions.subscribeTo(stableSelector, triggerOnSubscribe = false, listener = listener)
    }

/**
 * Property-reference convenience for this [SelectorStore]'s shared
 * subscription group. Property references are stable and need no key.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public fun <S, F> SelectorStore<S>.fieldState(property: KProperty1<S, F>): State<F> =
    (this as Store<S>).fieldState(subscriptions, property)

@Composable
private fun <S, F> Store<S>.selectorStateBinding(
    selectorKey: Any?,
    selector: (S) -> F,
    subscriptionKey: Any?,
    subscribe: ((S) -> F, (F, F) -> Unit) -> StoreSubscription,
): State<F> {
    val store = this
    val rememberedSelector = remember(store, selectorKey) { selector }
    val tick = remember(store, rememberedSelector, subscriptionKey) { mutableIntStateOf(0) }
    val initial = remember(store, rememberedSelector, subscriptionKey) { rememberedSelector(store.state) }
    DisposableEffect(store, rememberedSelector, subscriptionKey) {
        val subscription = subscribe(rememberedSelector) { _, _ -> tick.intValue++ }
        // Subscribe first, then re-sample. This closes the window between the
        // initial read and effect installation without an unconditional mount bump.
        if (rememberedSelector(store.state) != initial) tick.intValue++
        onDispose { subscription() }
    }
    return remember(store, rememberedSelector, subscriptionKey) {
        object : State<F> {
            override val value: F
                get() {
                    @Suppress("UNUSED_EXPRESSION")
                    tick.intValue
                    return rememberedSelector(store.state)
                }
        }
    }
}
