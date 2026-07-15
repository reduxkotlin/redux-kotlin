package org.reduxkotlin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import org.reduxkotlin.Store
import org.reduxkotlin.granular.SelectorSubscriptions

/**
 * A stable, Compose-root-scoped [Store] facade for selector bindings.
 *
 * Create one instance with [rememberSelectorStore] near the root of each
 * Compose composition and pass it to the screens and components that bind
 * state. All [selectorState] and [fieldState] calls made through the same
 * facade share one underlying store callback while retaining independent
 * equality checks and recomposition isolation for every selected value.
 *
 * Bindings remove themselves when their composables leave composition. A
 * single-store application normally needs one `SelectorStore` per Compose
 * root; separate windows, independently mounted compositions, or distinct
 * Redux stores need separate instances.
 *
 * Shared delivery reduces store notification fan-out, but every active
 * selector is still compared after an update. Use
 * `org.reduxkotlin.granular.memoizedSelector` for expensive derived
 * projections, and bind the narrowest value rather than reading [state]
 * directly from composition.
 */
@Stable
public class SelectorStore<S> internal constructor(
    private val delegate: Store<S>,
    internal val subscriptions: SelectorSubscriptions<S>,
) : Store<S> by delegate

/**
 * Remembers a [SelectorStore] for [store].
 *
 * The returned facade is stable until [store] changes and closes its shared
 * selector-subscription group when this call site leaves composition. Pass
 * the same facade to descendants that should share delivery. Selectors that
 * capture a changing Compose value must use the keyed [SelectorStore.selectorState]
 * overload.
 */
@Composable
public fun <S> rememberSelectorStore(store: Store<S>): SelectorStore<S> {
    val subscriptions = store.rememberSelectorSubscriptions()
    return remember(store, subscriptions) { SelectorStore(store, subscriptions) }
}
