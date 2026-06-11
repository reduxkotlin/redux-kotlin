import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-all")
    id("convention.publishing-mpp")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // iosX64/macosX64 omitted: base redux-kotlin does not target them; add when the base does.
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
                implementation(libs.kotlinx.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmCommonMain") {
            dependencies {
                implementation(libs.kotlin.reflect)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                dependencies {
                    implementation(libs.kotlin.reflect)
                }
            }
        }
    }
}
