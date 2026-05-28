package org.reduxkotlin.concurrent.benchmarks

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Group
import org.openjdk.jmh.annotations.GroupThreads
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.reduxkotlin.Reducer
import org.reduxkotlin.Store
import org.reduxkotlin.concurrent.createConcurrentStore
import org.reduxkotlin.createStore
import org.reduxkotlin.threadsafe.createThreadSafeStore
import java.util.concurrent.TimeUnit

/**
 * Read latency under a concurrent writer storm — the metric the module exists
 * to improve. SampleTime captures p50/p99/p999 of getState(). Compare the
 * "concurrent" host (lock-free reads) against "threadsafe" (readers block) and
 * "plain" (single-threaded latency reference).
 *
 * Run:  ./gradlew :redux-kotlin-concurrent:jvmBenchmarkBenchmark
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 8, time = 2)
@State(Scope.Group)
open class ReadContentionBenchmark {

    @Param("concurrent", "threadsafe", "plain")
    var host: String = "concurrent"

    private lateinit var store: Store<CounterState>

    @Setup(Level.Trial)
    fun setUp() {
        store = when (host) {
            "concurrent" -> createConcurrentStore(counterReducer, CounterState())
            "threadsafe" -> createThreadSafeStore(counterReducer, CounterState())
            else -> createStore(counterReducer, CounterState())
        }
    }

    @Benchmark
    @Group("contention")
    @GroupThreads(2)
    fun writerDispatch() {
        store.dispatch(IncrementAction)
    }

    @Benchmark
    @Group("contention")
    @GroupThreads(4)
    fun readerGetState(): Int = store.getState().counter
}

internal data class CounterState(val counter: Int = 0)
internal object IncrementAction
internal val counterReducer: Reducer<CounterState> = { s, a ->
    if (a is IncrementAction) s.copy(counter = s.counter + 1) else s
}
