package org.reduxkotlin.concurrent.benchmarks

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
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import org.reduxkotlin.Store
import org.reduxkotlin.concurrent.createConcurrentStore
import org.reduxkotlin.createStore
import org.reduxkotlin.threadsafe.createThreadSafeStore
import java.util.concurrent.TimeUnit

/**
 * Writer throughput/overhead with a ZERO-ALLOCATION (identity) reducer, isolating
 * sequencer/lock/mirror-publish cost. Writer throughput is a PRE-REGISTERED
 * NON-IMPROVEMENT vs threadsafe (fan-out runs under the lock); this guards against
 * regression and measures per-dispatch allocation via -prof gc.
 *
 * Run: ./gradlew :redux-kotlin-concurrent:jvmBenchmarkBenchmark -Pjmh.prof=gc
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
open class WriterBenchmark {

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
    @Threads(1)
    fun dispatch_1t() = store.dispatch(IncrementAction)

    @Benchmark
    @Threads(4)
    fun dispatch_4t() = store.dispatch(IncrementAction)
}
