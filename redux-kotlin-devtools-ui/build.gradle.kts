import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved-composeui")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("convention.publishing-mpp")
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.devtools.ui"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-devtools-core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
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
    }
}

// CI's Xcode toolchain can't link this module's iOS test binary: its Compose UI
// rendering tests pull compose ui-uikit, which auto-links the private
// 'UIUtilities' framework the linker can't find ("Undefined symbols for
// architecture arm64"). Those UI tests run on the JVM; disable the iOS test
// build. The iOS *main* targets still compile and publish.
tasks.configureEach {
    if (name == "linkDebugTestIosSimulatorArm64" ||
        name == "linkDebugTestIosArm64" ||
        name == "iosSimulatorArm64Test"
    ) {
        enabled = false
    }
}
