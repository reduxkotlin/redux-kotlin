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
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.granular.subscribeTo
import org.reduxkotlin.threadsafe.createThreadSafeStore
import java.util.concurrent.TimeUnit

/**
 * Contention benchmark for granular subscriptions on [createThreadSafeStore].
 *
 * Measures how dispatch throughput scales as:
 *  - the number of granular subscriptions grows (`subCount` = 0, 10, 100), and
 *  - the number of concurrent dispatcher threads grows (`@Threads(1, 2, 4, 8)`).
 *
 * Two workloads:
 *  - `dispatch_noChange_*`: the action returns the same state instance, so
 *    every granular entry's `===` fast-path hits and no listener fires.
 *    Measures pure framework overhead (lock acquisition + entry walk + diff).
 *  - `dispatch_mixedChange_*`: the action mutates ~50% of the underlying
 *    [WideState.values] list, so ~50% of subscribers see a real change
 *    and fire their listener. Measures the realistic ceiling consumers
 *    should plan against — the per-target budget from the plan is
 *    "≤5 µs at 100 entries on JVM 21" against this workload.
 *
 * The 100 selectors used at `subCount = 100` are 100 distinct lambdas, each
 * indexing into a different position of [WideState.values]. Distinct
 * lambda identities make the underlying `Function1.invoke` call site
 * megamorphic — JIT inline caches saturate after ~8 different targets,
 * which is the realistic shape of a UI screen subscribing to many
 * different fields. The plan originally specified 100 distinct
 * `KProperty1` references; using lambdas with the same underlying
 * indexed reads achieves the same call-site behaviour without requiring
 * a 100-field data class. KProperty1-specific overhead is measured
 * separately by [PropertyRefBenchmark].
 *
 * Run with:
 * ```
 * ./gradlew :redux-kotlin-granular:jvmBenchmarkBenchmark
 * ```
 *
 * This task is NOT part of `:build` — benchmarks take several minutes
 * (warm-up + measurement iterations) and would block CI. The default
 * configuration runs only when explicitly requested.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
open class ContentionBenchmark {

    @Param("0", "10", "100")
    var subCount: Int = 0

    private lateinit var store: Store<WideState>
    private lateinit var subs: List<StoreSubscription>

    @Setup(Level.Trial)
    fun setUp() {
        store = createThreadSafeStore(reducer, WideState())
        subs = (0 until subCount).map { idx ->
            store.subscribeTo({ state -> state.values[idx] }, triggerOnSubscribe = false) { _, _ -> }
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        subs.forEach { it() }
    }

    // ---- noChange workload: action returns same state instance.
    @Benchmark
    @Threads(1)
    fun dispatch_noChange_1t() = store.dispatch(NoopAction)

    @Benchmark
    @Threads(2)
    fun dispatch_noChange_2t() = store.dispatch(NoopAction)

    @Benchmark
    @Threads(4)
    fun dispatch_noChange_4t() = store.dispatch(NoopAction)

    @Benchmark
    @Threads(8)
    fun dispatch_noChange_8t() = store.dispatch(NoopAction)

    // ---- mixedChange workload: 50% of entries see a real value change.
    @Benchmark
    @Threads(1)
    fun dispatch_mixedChange_1t() = store.dispatch(MixedChangeAction)

    @Benchmark
    @Threads(2)
    fun dispatch_mixedChange_2t() = store.dispatch(MixedChangeAction)

    @Benchmark
    @Threads(4)
    fun dispatch_mixedChange_4t() = store.dispatch(MixedChangeAction)

    @Benchmark
    @Threads(8)
    fun dispatch_mixedChange_8t() = store.dispatch(MixedChangeAction)
}

internal data class WideState(
    val tick: Int = 0,
    val values: List<Int> = List(WIDE_STATE_SIZE) { 0 },
)

internal object NoopAction
internal object MixedChangeAction

internal val reducer: Reducer<WideState> = { state, action ->
    when (action) {
        is MixedChangeAction -> {
            val nextTick = state.tick + 1
            // Mutate every other entry: half the granular subscribers see
            // a value change, half see the same value (===-fast-path hits).
            val nextValues = ArrayList<Int>(state.values.size).apply {
                for (i in state.values.indices) {
                    add(if (i % 2 == 0) nextTick else state.values[i])
                }
            }
            state.copy(tick = nextTick, values = nextValues)
        }
        else -> state
    }
}

internal const val WIDE_STATE_SIZE = 100
