package org.reduxkotlin.compose.multimodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import org.reduxkotlin.Store
import org.reduxkotlin.compose.SelectorStore
import org.reduxkotlin.compose.selectorState
import org.reduxkotlin.granular.SelectorSubscriptions
import org.reduxkotlin.multimodel.ModelState
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Returns a Compose [State] that always reflects `state.get<M>().property`
 * on a `Store<ModelState>`. The model type [M] is inferred from the
 * property reference's receiver — the call site does not name
 * [ModelState] at all.
 *
 * ```
 * @Composable
 * fun ProfileHeader(store: Store<ModelState>) {
 *     val displayName by store.fieldState(LoggedInUserModel::displayName)
 *     Text("Hello, $displayName")
 * }
 * ```
 *
 * Same re-sample-inside-DisposableEffect race fix (review B3) as the
 * single-model bridge — the implementation forwards to
 * [selectorState] with a `{ state.get<M>().property }` closure, so the
 * Compose-side lifecycle and granular-side subscription work identically.
 *
 * Hidden from Swift via [HiddenFromObjC] (KProperty1 is Kotlin-only)
 * and not directly callable from raw JS/TS (inline + reified). The
 * lambda-form [selectorState] covers both non-Kotlin paths.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public inline fun <reified M : Any, F> Store<ModelState>.fieldState(property: KProperty1<M, F>): State<F> =
    selectorState { state -> property.get(state.get<M>()) }

/**
 * Shared-subscription counterpart of [fieldState]. The [subscriptions] scope
 * must have been created from this store and can be shared by sibling model
 * bindings in the same Compose subtree.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public inline fun <reified M : Any, F> Store<ModelState>.fieldState(
    subscriptions: SelectorSubscriptions<ModelState>,
    property: KProperty1<M, F>,
): State<F> = selectorState(subscriptions) { state -> property.get(state.get<M>()) }

/**
 * Non-inline [KClass]-keyed alternative to the reified [fieldState]
 * overload, for callers that hold the model type as a [KClass] rather
 * than as a compile-time generic (review I11 — inline reified is
 * erased from generated `.d.ts` for raw JS/TS consumers).
 */
@Composable
public fun <M : Any, F> Store<ModelState>.fieldStateOf(modelClass: KClass<M>, selector: (M) -> F): State<F> =
    selectorState { state -> selector(state.get(modelClass)) }

/**
 * Keyed counterpart of [fieldStateOf] that replaces the retained selector
 * whenever [key] changes. Use it when [selector] captures a changing id,
 * filter, or other Compose value.
 */
@Composable
public fun <M : Any, F> Store<ModelState>.fieldStateOf(
    key: Any?,
    modelClass: KClass<M>,
    selector: (M) -> F,
): State<F> = selectorState(key) { state -> selector(state.get(modelClass)) }

/**
 * Shared-subscription counterpart of [fieldStateOf]. The [subscriptions]
 * scope must have been created from this store.
 */
@Composable
public fun <M : Any, F> Store<ModelState>.fieldStateOf(
    subscriptions: SelectorSubscriptions<ModelState>,
    modelClass: KClass<M>,
    selector: (M) -> F,
): State<F> = selectorState(subscriptions) { state -> selector(state.get(modelClass)) }

/**
 * Keyed shared-subscription counterpart of [fieldStateOf]. The selector is
 * replaced whenever [key] changes while remaining in [subscriptions].
 */
@Composable
public fun <M : Any, F> Store<ModelState>.fieldStateOf(
    subscriptions: SelectorSubscriptions<ModelState>,
    key: Any?,
    modelClass: KClass<M>,
    selector: (M) -> F,
): State<F> = selectorState(subscriptions, key) { state -> selector(state.get(modelClass)) }

/**
 * Returns a Compose [State] for a slice of [modelClass] through this
 * [SelectorStore]'s shared subscription group.
 */
@Composable
public fun <M : Any, F> SelectorStore<ModelState>.fieldStateOf(modelClass: KClass<M>, selector: (M) -> F): State<F> =
    selectorState { state -> selector(state.get(modelClass)) }

/**
 * Keyed [SelectorStore] counterpart of [fieldStateOf]. Use [key] when
 * [selector] captures a changing Compose value.
 */
@Composable
public fun <M : Any, F> SelectorStore<ModelState>.fieldStateOf(
    key: Any?,
    modelClass: KClass<M>,
    selector: (M) -> F,
): State<F> = selectorState(key) { state -> selector(state.get(modelClass)) }
