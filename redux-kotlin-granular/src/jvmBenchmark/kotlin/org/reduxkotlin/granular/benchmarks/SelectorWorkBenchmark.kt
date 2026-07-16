package org.reduxkotlin.granular.benchmarks

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.reduxkotlin.Store
import org.reduxkotlin.granular.memoizedSelector
import org.reduxkotlin.granular.selectorSubscriptions
import org.reduxkotlin.granular.subscribeTo
import org.reduxkotlin.threadsafe.createThreadSafeStore
import java.util.concurrent.TimeUnit

/**
 * Measures the two independent selector-work optimizations:
 *
 * * `dispatch_sharedFanout` compares N independent store subscribers with one
 *   dynamic shared scope. Both shapes still evaluate N simple selectors.
 * * `dispatch_memoizedTransform` compares a plain projection with an explicit
 *   input-memoized projection when the dispatch leaves that input unchanged.
 *
 * Run with `./gradlew :redux-kotlin-granular:jvmBenchmarkBenchmark`.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
open class SelectorWorkBenchmark {

    @Param("10", "100")
    var selectorCount: Int = 0

    private lateinit var independentStore: Store<WideState>
    private lateinit var sharedStore: Store<WideState>
    private lateinit var plainTransformStore: Store<WideState>
    private lateinit var memoizedTransformStore: Store<WideState>
    private lateinit var sharedSubscriptions: org.reduxkotlin.granular.SelectorSubscriptions<WideState>

    @Setup(Level.Trial)
    fun setUp() {
        independentStore = createThreadSafeStore(reducer, WideState())
        sharedStore = createThreadSafeStore(reducer, WideState())
        plainTransformStore = createThreadSafeStore(reducer, WideState())
        memoizedTransformStore = createThreadSafeStore(reducer, WideState())

        repeat(selectorCount) { index ->
            independentStore.subscribeTo({ state -> state.values[index] }, triggerOnSubscribe = false) { _, _ -> }
        }
        sharedSubscriptions = sharedStore.selectorSubscriptions()
        repeat(selectorCount) { index ->
            sharedSubscriptions.subscribeTo({ state -> state.values[index] }, triggerOnSubscribe = false) { _, _ -> }
        }

        plainTransformStore.subscribeTo(
            selector = { state -> expensiveProjection(state.values) },
            triggerOnSubscribe = false,
        ) { _, _ -> }
        val memoizedProjection = memoizedSelector(
            inputSelector = { state: WideState -> state.values },
            transform = ::expensiveProjection,
        )
        memoizedTransformStore.subscribeTo(memoizedProjection, triggerOnSubscribe = false) { _, _ -> }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        sharedSubscriptions.close()
    }

    @Benchmark
    fun dispatch_independentFanout() = independentStore.dispatch(NoopAction)

    @Benchmark
    fun dispatch_sharedFanout() = sharedStore.dispatch(NoopAction)

    @Benchmark
    fun dispatch_plainTransform() = plainTransformStore.dispatch(NoopAction)

    @Benchmark
    fun dispatch_memoizedTransform() = memoizedTransformStore.dispatch(NoopAction)
}

private fun expensiveProjection(values: List<Int>): Int {
    var sum = 0
    repeat(32) {
        values.forEach { value -> sum += value }
    }
    return sum
}
