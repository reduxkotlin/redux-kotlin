package org.reduxkotlin.bundle

internal data class CounterModel(val count: Int = 0)

internal data class Increment(val by: Int)
internal object Reset

internal fun counterInitial(): CounterModel = CounterModel()
internal fun onIncrement(s: CounterModel, a: Increment): CounterModel = s.copy(count = s.count + a.by)
@Suppress("UnusedParameter")
internal fun onReset(s: CounterModel, a: Reset): CounterModel = CounterModel()
