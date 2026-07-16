import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import util.jvmCommonTest

plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

allOpen {
    // JMH requires @State-annotated classes to be open so it can subclass them.
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    android {
        namespace = "org.reduxkotlin.granular"
    }

    // Dedicated benchmark compilation under the jvm target, associated
    // with main so it sees the public API without having to depend on
    // it transitively. Sources live under src/jvmBenchmark/kotlin.
    jvm {
        compilations {
            val main by getting
            create("benchmark") { associateWith(main) }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(libs.kotlinx.atomicfu)
            }
        }
        jvmCommonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                // Concurrency stress tests use the thread-safe store as
                // the canonical multi-threaded host.
                implementation(project(":redux-kotlin-threadsafe"))
                // Tripwire: granular diffing over the concurrent store relies on
                // the fan-out staying under the writer lock (serial delivery).
                implementation(project(":redux-kotlin-concurrent"))
            }
        }
        named("jvmBenchmark") {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(project(":redux-kotlin-threadsafe"))
            }
        }
    }
}

benchmark {
    targets {
        register("jvmBenchmark") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.37"
        }
    }
}
