package org.reduxkotlin.routing.sample

import org.reduxkotlin.routing.Reduce
import org.reduxkotlin.routing.ReduxInitial

internal data class CounterModel(val count: Int = 0)

internal data class Increment(val by: Int)
internal object Reset

@ReduxInitial internal fun counterInitial(): CounterModel = CounterModel()

@Reduce internal fun onIncrement(s: CounterModel, a: Increment): CounterModel = s.copy(count = s.count + a.by)

@Suppress("UnusedParameter")
@Reduce internal fun onReset(s: CounterModel, a: Reset): CounterModel = CounterModel()
