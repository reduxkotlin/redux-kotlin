import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("convention.publishing-mpp")
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
        named("jvmTest") {
            dependencies {
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
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
