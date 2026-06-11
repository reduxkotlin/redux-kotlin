import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // iosX64/macosX64 omitted: base redux-kotlin does not target them; add when the base does.
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.devtools.bridge"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-devtools-core"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmCommonMain") {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                dependencies {
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
