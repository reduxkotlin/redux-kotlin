package org.reduxkotlin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import org.reduxkotlin.Store
import org.reduxkotlin.granular.subscribeTo
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.reflect.KProperty1

/**
 * Returns a Compose [State] that always reflects `selector(store.state)`.
 *
 * The value is read synchronously from the store on every [State.value]
 * access — the [State] re-evaluates `selector(store.state)` each read.
 * The store subscription is used **only** to schedule recomposition: when
 * the selected value changes (`===` then `==`) the subscriber bumps an
 * internal tick that the value getter observes, so reading Composables
 * recompose. Updates fire only when the selected value actually changes,
 * so a `Store<S>` with N `selectorState` calls only recomposes the subset
 * of UI that reads fields that actually moved.
 *
 * Because the value is always pulled from `store.state` at read time, the
 * binding is correct even when the store delivers notifications
 * asynchronously (e.g. a concurrent store that posts callbacks to the main
 * thread): a recomposition triggered by anything — including an unrelated
 * state change — reads the freshest store state, never a stale cached
 * snapshot lagging a frame behind the notification.
 *
 * A B3 re-sample runs inside the [DisposableEffect], AFTER the subscription is installed: it
 * compares the value at first composition against the current value and bumps the tick **only if
 * it changed**. The subscribe-then-resample order is load-bearing — every change before the
 * install is caught by the re-sample, every change after it by the subscription, so no window
 * remains (the worst-case overlap is one redundant same-value bump). This catches a change that
 * landed between first composition and the install (e.g. a fast async load), for which no real
 * notification is delivered — without it the binding would stay stuck on the first-frame value.
 * Because the bump is conditional, an unchanged value adds no mount-time recomposition (so render
 * isolation is preserved: a binding only recomposes when its selected value actually moves).
 *
 * The captured [selector] lambda is stable across recompositions: it
 * is remembered against `this` (the store). If the selector closes
 * over outer Composable state that should refresh the binding, the
 * outer state is **frozen at first composition** — use
 * [fieldState] with a property reference instead, or unsubscribe and
 * resubscribe by hand inside a `LaunchedEffect` keyed on the variable.
 *
 * The [selector] runs on every read; if it is expensive, hoist/memoize it outside the composable.
 */
@Composable
public fun <S, F> Store<S>.selectorState(selector: (S) -> F): State<F> {
    val store = this
    val rememberedSelector = remember(store) { selector }
    val tick = remember(store, rememberedSelector) { mutableIntStateOf(0) }
    // Value observed at first composition; the effect compares against it to detect a change that
    // landed before the subscription was installed.
    val initial = remember(store, rememberedSelector) { rememberedSelector(store.state) }
    DisposableEffect(store, rememberedSelector) {
        // Install the subscription FIRST, then re-sample — the order is load-bearing: a change
        // landing before the install is caught by the re-sample below; a change landing after it
        // is caught by the subscription. With the reverse order, a change landing between the
        // re-sample and the install (e.g. inside subscribe() itself, or a fast off-main dispatch)
        // was never observed. Worst-case overlap is one redundant same-value tick bump.
        val sub = store.subscribeTo(
            selector = rememberedSelector,
            triggerOnSubscribe = false,
        ) { _, _ ->
            tick.intValue++
        }
        // B3 re-sample: if the selected value changed between first composition and this point
        // (e.g. a fast async load), bump the tick so the getter re-reads. Conditional, so an
        // unchanged value adds no mount-time recomposition (preserves render isolation).
        if (rememberedSelector(store.state) != initial) tick.intValue++
        onDispose { sub() }
    }
    return remember(store, rememberedSelector) {
        object : State<F> {
            override val value: F
                get() {
                    // Observe the tick so this read recomposes when the subscription reports a change;
                    // the value itself is always read fresh from getState() below.
                    @Suppress("UNUSED_EXPRESSION")
                    tick.intValue
                    return rememberedSelector(store.state)
                }
        }
    }
}

/**
 * Property-reference convenience overload of [selectorState] for the
 * single-field case. Equivalent to `selectorState(property::get)` but
 * lets the call site use `store.fieldState(MyState::myField)`.
 *
 * Like [selectorState], the value is read synchronously from
 * `store.state` on every [State.value] access; the subscription only
 * schedules recomposition, so the value is correct even when the store
 * delivers notifications asynchronously (e.g. a concurrent store posting
 * to the main thread). Like [selectorState] it installs the subscription and then runs a
 * conditional B3 re-sample, so a change landing any time before the install is caught.
 *
 * Hidden from Swift via [HiddenFromObjC] (KProperty1 is Kotlin-only).
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Composable
public fun <S, F> Store<S>.fieldState(property: KProperty1<S, F>): State<F> {
    val store = this
    val tick = remember(store, property) { mutableIntStateOf(0) }
    // Value observed at first composition; the effect compares against it to detect a change that
    // landed before the subscription was installed.
    val initial = remember(store, property) { property.get(store.state) }
    DisposableEffect(store, property) {
        // Subscribe first, then re-sample (see selectorState — the order is load-bearing).
        val sub = store.subscribeTo(
            property = property,
            triggerOnSubscribe = false,
        ) { _, _ ->
            tick.intValue++
        }
        // B3 re-sample: bump only if the value changed between first composition and this point,
        // so an unchanged value adds no mount-time recomposition.
        if (property.get(store.state) != initial) tick.intValue++
        onDispose { sub() }
    }
    return remember(store, property) {
        object : State<F> {
            override val value: F
                get() {
                    // Observe the tick so this read recomposes when the subscription reports a change;
                    // the value itself is always read fresh from getState() below.
                    @Suppress("UNUSED_EXPRESSION")
                    tick.intValue
                    return property.get(store.state)
                }
        }
    }
}
