package org.reduxkotlin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import org.reduxkotlin.Store
import kotlin.jvm.JvmInline

/**
 * Compose-stable wrapper around a [Store].
 *
 * Compose's stability inferrer treats interfaces as `@Unstable`, which
 * means any Composable that takes a `Store<S>` directly as a parameter
 * becomes non-skippable — the runtime cannot prove the reference is
 * unchanged between recompositions, so it recomposes unconditionally.
 *
 * Wrapping the store in this `@Stable` class restores skippability for
 * downstream Composables that take a [StableStore] parameter, without
 * requiring any change to the store implementation itself. For Compose state
 * bindings, prefer [SelectorStore] from [rememberSelectorStore]: it is also
 * stable, exposes [SelectorStore.selectorState] and [SelectorStore.fieldState]
 * directly, provides dispatch without exposing the raw store, and shares one
 * store callback.
 *
 * Usage:
 * ```
 * @Composable
 * val bindings = rememberSelectorStore(store)
 * val user by bindings.fieldState(AppState::user)
 * bindings.dispatch(RefreshUser)
 * ```
 *
 * The wrapper is a value class to keep the runtime overhead at zero
 * once the JIT has inlined it.
 *
 * Use this compatibility wrapper only when another API requires a stable
 * [Store] reference and cannot accept [SelectorStore].
 */
@Stable
@JvmInline
@Deprecated(
    message = "StableStore exposes the raw Store to Compose. Use rememberSelectorStore for selection and dispatch.",
)
public value class StableStore<S>(
    /** The wrapped store for compatibility with APIs that require [Store]. */
    public val value: Store<S>,
)

/**
 * Convenience that wraps [store] in a [StableStore] and remembers it
 * across recompositions, keyed by the store identity. Equivalent to
 * `remember(store) { StableStore(store) }`.
 */
@Composable
@Suppress("DEPRECATION")
@Deprecated(
    message = "StableStore exposes the raw Store to Compose. Use rememberSelectorStore for selection and dispatch.",
    replaceWith = ReplaceWith(
        expression = "rememberSelectorStore(store)",
        imports = ["org.reduxkotlin.compose.rememberSelectorStore"],
    ),
)
public fun <S> rememberStableStore(store: Store<S>): StableStore<S> = remember(store) { StableStore(store) }
