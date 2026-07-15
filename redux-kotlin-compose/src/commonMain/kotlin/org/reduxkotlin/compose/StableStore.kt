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
 * requiring any change to the store implementation itself.
 *
 * Usage:
 * ```
 * @Composable
 * fun App(store: Store<AppState>) {
 *     val stable = rememberStableStore(store)
 *     Content(stable)
 * }
 *
 * @Composable
 * fun Content(store: StableStore<AppState>) {
 *     val user by store.value.fieldState(AppState::user)
 *     // …
 * }
 * ```
 *
 * The wrapper is a value class to keep the runtime overhead at zero
 * once the JIT has inlined it.
 *
 * For Compose selector bindings that should also share one underlying store
 * callback, use [SelectorStore] from [rememberSelectorStore] instead.
 */
@Stable
@JvmInline
public value class StableStore<S>(
    /** The wrapped store. Access via `myStableStore.value`. */
    public val value: Store<S>,
)

/**
 * Convenience that wraps [store] in a [StableStore] and remembers it
 * across recompositions, keyed by the store identity. Equivalent to
 * `remember(store) { StableStore(store) }`.
 */
@Composable
public fun <S> rememberStableStore(store: Store<S>): StableStore<S> = remember(store) { StableStore(store) }
