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
            namespace = "org.reduxkotlin.compose.saveable"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-compose"))
                implementation(compose.runtime)
                implementation(libs.compose.runtime.saveable)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        named("jvmTest") {
            dependencies {
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.foundation)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
