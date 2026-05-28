package org.reduxkotlin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.reduxkotlin.Store
import org.reduxkotlin.granular.subscribeTo
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.reflect.KProperty1

/**
 * Returns a Compose [State] that always reflects `selector(store.state)`.
 *
 * On the dispatch that follows a state change, the [State.value] is
 * updated and any Composable that read it during composition is
 * scheduled for recomposition. Updates fire only when the selected
 * value actually changes (`===` then `==`), so a `Store<S>` with N
 * `selectorState` calls only recomposes the subset of UI that reads
 * fields that actually moved.
 *
 * The bridge uses [granular subscribeTo][subscribeTo] with
 * `triggerOnSubscribe = false` and re-samples the current state
 * **inside** [DisposableEffect]. The re-sample closes the
 * init-to-subscribe race window: between `remember { mutableStateOf(...) }`
 * (which runs during composition) and the effect block (which runs at
 * commit), the store may have dispatched a state change. Without the
 * re-sample, the first frame can render a stale value until the next
 * dispatch arrives. Setting `triggerOnSubscribe = false` avoids a
 * redundant `mutableState.value = mutableState.value` assignment after
 * the explicit re-sample.
 *
 * The captured [selector] lambda is stable across recompositions: it
 * is remembered against `this` (the store). If the selector closes
 * over outer Composable state that should refresh the binding, the
 * outer state is **frozen at first composition** — use
 * [fieldState] with a property reference instead, or unsubscribe and
 * resubscribe by hand inside a `LaunchedEffect` keyed on the variable.
 */
@Composable
public fun <S, F> Store<S>.selectorState(selector: (S) -> F): State<F> {
    val store = this
    val rememberedSelector = remember(store) { selector }
    val mutableState = remember(store, rememberedSelector) {
        mutableStateOf(rememberedSelector(store.state))
    }
    DisposableEffect(store, rememberedSelector) {
        // B3 race fix: dispatches that landed between composition and
        // this effect's invocation are not observable through the cached
        // initial value above. Re-sample under the effect so the first
        // frame after the effect runs reflects current state.
        mutableState.value = rememberedSelector(store.state)
        val sub = store.subscribeTo(
            selector = rememberedSelector,
            triggerOnSubscribe = false,
        ) { _, new ->
            mutableState.value = new
        }
        onDispose { sub() }
    }
    return mutableState
}

/**
 * Property-reference convenience overload of [selectorState] for the
 * single-field case. Equivalent to `selectorState(property::get)` but
 * lets the call site use `store.fieldState(MyState::myField)`.
 *
 * Hidden from Swift via [HiddenFromObjC] (KProperty1 is Kotlin-only).
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public fun <S, F> Store<S>.fieldState(property: KProperty1<S, F>): State<F> {
    val store = this
    val mutableState = remember(store, property) {
        mutableStateOf(property.get(store.state))
    }
    DisposableEffect(store, property) {
        // Same B3 re-sample as selectorState.
        mutableState.value = property.get(store.state)
        val sub = store.subscribeTo(
            property = property,
            triggerOnSubscribe = false,
        ) { _, new ->
            mutableState.value = new
        }
        onDispose { sub() }
    }
    return mutableState
}
