package org.reduxkotlin.granular.benchmarks

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.granular.subscribeTo
import org.reduxkotlin.threadsafe.createThreadSafeStore
import java.util.concurrent.TimeUnit

/**
 * Isolated comparison between two selector shapes used by granular
 * subscriptions:
 *
 *  - **Property-reference selector** (`subscribeTo(MyState::field)`).
 *    Internally this is `property::get`, which JIT can specialise per
 *    property singleton. The call site sees the same `KProperty1`
 *    instance every time, so dispatch tends to monomorphise.
 *
 *  - **Boxed-primitive selector** (`subscribeTo { state -> state.counter }`
 *    returning `Int`). Because `Function1.invoke` operates on `Any?`,
 *    every primitive return is autoboxed into an `Integer`; the granular
 *    layer's `===` fast-path then misses (different `Integer` instances),
 *    falls through to `==`, which unboxes both sides for comparison.
 *
 * Both benchmarks subscribe a single granular entry against the same
 * counter field. The delta between them is the autoboxing-plus-fallback
 * cost surfaced by review B2.
 *
 * Single-threaded; the contention story is covered by
 * [ContentionBenchmark].
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@Threads(1)
open class PropertyRefBenchmark {

    private lateinit var propertyRefStore: Store<CounterState>
    private lateinit var lambdaStore: Store<CounterState>

    @Setup(Level.Trial)
    fun setUp() {
        propertyRefStore = createThreadSafeStore(counterReducer, CounterState())
        lambdaStore = createThreadSafeStore(counterReducer, CounterState())

        // Property-reference subscription — KProperty1<CounterState, Int>.
        propertyRefStore.subscribeTo(CounterState::counter, triggerOnSubscribe = false) { _, _ -> }

        // Lambda selector returning a primitive Int — boxes through Function1.
        lambdaStore.subscribeTo({ state -> state.counter }, triggerOnSubscribe = false) { _, _ -> }
    }

    @Benchmark
    fun dispatch_propertyRefSelector() = propertyRefStore.dispatch(IncrementAction)

    @Benchmark
    fun dispatch_lambdaPrimitiveSelector() = lambdaStore.dispatch(IncrementAction)
}

internal data class CounterState(val counter: Int = 0)
internal object IncrementAction
internal val counterReducer: Reducer<CounterState> = { state, action ->
    when (action) {
        is IncrementAction -> state.copy(counter = state.counter + 1)
        else -> state
    }
}
