plugins {
    id("convention.library-mpp-loved")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("convention.publishing-mpp")
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
            namespace = "org.reduxkotlin.devtools.inapp"
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(compose.runtime)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        // compose-foundation publishes no linuxX64/mingwX64 klibs, so the HostFrame actual that
        // mirrors the debug host's Box(Modifier.fillMaxSize()) frame lives only in the source sets
        // below; linuxMain/mingwMain fall back to rendering the content directly.
        named("jvmCommonMain") {
            dependencies {
                implementation(compose.foundation)
            }
        }
        named("jsMain") {
            dependencies {
                implementation(compose.foundation)
            }
        }
        named("wasmJsMain") {
            dependencies {
                implementation(compose.foundation)
            }
        }
        named("appleMain") {
            dependencies {
                implementation(compose.foundation)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                dependencies {
                    implementation(compose.foundation)
                }
            }
        }
    }
}
