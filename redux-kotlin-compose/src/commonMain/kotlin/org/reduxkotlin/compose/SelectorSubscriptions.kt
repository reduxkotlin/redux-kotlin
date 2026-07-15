package org.reduxkotlin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.reduxkotlin.Store
import org.reduxkotlin.granular.SelectorSubscriptions
import org.reduxkotlin.granular.selectorSubscriptions

/**
 * Creates and remembers a shared granular subscription scope for this Compose
 * subtree. Pass the returned scope to sibling `selectorState` or `fieldState`
 * bindings to use one underlying store callback; Compose closes it when the
 * owning subtree leaves composition.
 */
@Composable
public fun <S> Store<S>.rememberSelectorSubscriptions(): SelectorSubscriptions<S> {
    val store = this
    val subscriptions = remember(store) { store.selectorSubscriptions() }
    DisposableEffect(subscriptions) {
        onDispose { subscriptions.close() }
    }
    return subscriptions
}
