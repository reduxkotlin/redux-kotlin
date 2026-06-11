import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

val hasAndroidSdk: Boolean = run {
    val localProps = rootProject.file("local.properties")
    val hasSdkInLocalProperties = localProps.exists() && localProps.readText().lineSequence().any {
        it.trim().startsWith("sdk.dir=") && it.substringAfter("sdk.dir=").isNotBlank()
    }
    val hasSdkInEnv =
        !System.getenv("ANDROID_HOME").isNullOrBlank() ||
            !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
    hasSdkInLocalProperties || hasSdkInEnv
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.concurrent"
        }
    }

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
        named("jvmBenchmark") {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(project(":redux-kotlin-threadsafe"))
            }
        }
        jvmTest {
            dependencies {
                // Race tests pin the publish-then-signal contract through the
                // granular diff layer (the consumer the C1 race actually bit).
                implementation(project(":redux-kotlin-granular"))
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
