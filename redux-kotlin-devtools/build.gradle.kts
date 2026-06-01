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
        // jvmCommonMain contains JVM-only APIs (kotlin.reflect.full, CIO engine).
        // The convention plugin would normally also add these files to androidMain via srcDir;
        // androidMain instead has its own actuals in src/androidMain/ below.
        named("jvmCommonMain") {
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.ktor.client.cio)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                // Override the convention plugin's srcDir injection: androidMain provides its own
                // actuals (src/androidMain/) and must NOT compile jvmCommonMain files that depend
                // on kotlin.reflect.full or the CIO engine, neither of which is available on Android.
                kotlin.setSrcDirs(listOf("src/androidMain/kotlin"))
                dependencies {
                    implementation(libs.ktor.client.okhttp)
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
