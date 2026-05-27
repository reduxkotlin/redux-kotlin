package org.reduxkotlin.examples.counter

import org.reduxkotlin.Reducer

/**
 * A richer counter state than the [Int] used by [reducer] above, designed
 * specifically to exercise granular subscriptions: multiple fields whose
 * values move independently in response to different actions, plus a
 * derived field (`isEven`) suitable for `subscribeTo({ selector })` /
 * `selectorState { … }` demos.
 *
 * Used by the `examples/counter/ios` Swift sample to validate the
 * `redux-kotlin-granular` Swift API ergonomics (`subscribeFields`,
 * lambda-overload `subscribeTo`, `@HiddenFromObjC` KProperty1 path).
 */
public data class GranularCounterState(
    val count: Int = 0,
    val label: String = "Counter",
    val lastAction: String = "<none>",
) {
    val isEven: Boolean get() = count % 2 == 0
}

public class GranularIncrement(public val amount: Int = 1)
public class GranularDecrement(public val amount: Int = 1)
public class GranularSetLabel(public val label: String)
public object GranularReset

public val granularCounterReducer: Reducer<GranularCounterState> = { state, action ->
    when (action) {
        is GranularIncrement -> state.copy(
            count = state.count + action.amount,
            lastAction = "Increment(${action.amount})",
        )
        is GranularDecrement -> state.copy(
            count = state.count - action.amount,
            lastAction = "Decrement(${action.amount})",
        )
        is GranularSetLabel -> state.copy(
            label = action.label,
            lastAction = "SetLabel(${action.label})",
        )
        is GranularReset -> GranularCounterState(lastAction = "Reset")
        else -> state
    }
}
