import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("convention.publishing-mpp")
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.compose.multimodel"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-compose"))
                api(project(":redux-kotlin-multimodel"))
                implementation(compose.runtime)
            }
        }
        named("jvmTest") {
            dependencies {
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
