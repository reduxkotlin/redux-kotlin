package org.reduxkotlin.granular

import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscription
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.reflect.KProperty1

/**
 * Property-reference convenience: subscribe to a single field, identified
 * by a Kotlin property reference like `AppState::user`.
 *
 * Kotlin call sites benefit from `::field` syntax sugar. From Swift this
 * overload is hidden via [@HiddenFromObjC] (KProperty1 is not constructible
 * from Swift); from JavaScript / TypeScript it lives in a file without
 * `@JsExport`, so it's only callable from Kotlin/JS code, not raw JS.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun <State, F> Store<State>.subscribeTo(
    property: KProperty1<State, F>,
    triggerOnSubscribe: Boolean = true,
    listener: (oldValue: F, newValue: F) -> Unit,
): StoreSubscription = subscribeTo(
    selector = property::get,
    triggerOnSubscribe = triggerOnSubscribe,
    listener = listener,
)
