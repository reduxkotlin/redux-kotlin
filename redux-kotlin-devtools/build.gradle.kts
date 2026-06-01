plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
    alias(libs.plugins.kotlin.serialization)
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
            namespace = "org.reduxkotlin.devtools"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        // jvmCommonMain holds the shared reflection serializer (kotlin.reflect.full) and the
        // CIO-based WebSocket engine. The convention plugin srcDir-shares this source into both
        // jvmMain and androidMain, but does NOT propagate its dependencies — so each consuming
        // source set must declare kotlin-reflect + the Ktor CIO engine itself (see androidMain).
        named("jvmCommonMain") {
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.ktor.client.cio)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                // androidMain compiles the shared jvmCommonMain source (reflection serializer +
                // CIO engine), so it needs those same deps declared here — they are not inherited.
                dependencies {
                    implementation(libs.kotlin.reflect)
                    implementation(libs.ktor.client.cio)
                }
            }
        }
        named("nativeMain") {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        named("jsMain") {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        named("wasmJsMain") {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
            }
        }
    }
}
