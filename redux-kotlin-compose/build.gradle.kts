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
            namespace = "org.reduxkotlin.compose"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-granular"))
                implementation(compose.runtime)
            }
        }
        named("jvmTest") {
            dependencies {
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.test)
                // Binding tests against the real concurrent store (test-scope only;
                // the published POM is unaffected).
                implementation(project(":redux-kotlin-concurrent"))
            }
        }
    }
}
