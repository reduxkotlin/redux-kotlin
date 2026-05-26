import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.control")
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.rssreader.kotlinx.serialization)
    alias(libs.plugins.rssreader.compose.multiplatform)
    alias(libs.plugins.rssreader.compose.compiler)
}

kotlin {
    androidLibrary {
        namespace = "com.github.jetbrains.rssreader.shared"
        compileSdk = libs.versions.rssreader.android.compile.sdk.get().toInt()
        minSdk = libs.versions.rssreader.android.min.sdk.get().toInt()

        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        androidResources { enable = true }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // Redux
            implementation(project(":redux-kotlin"))

            // Compose
            implementation(libs.rssreader.compose.runtime)
            implementation(libs.rssreader.compose.foundation)
            implementation(libs.rssreader.compose.material3)
            implementation(libs.rssreader.compose.ui)
            implementation(libs.rssreader.compose.components.resources)
            implementation(libs.rssreader.compose.ui.tooling.preview)
            implementation(libs.rssreader.material.icons.core)

            // Compose ecosystem
            implementation(libs.rssreader.coil.compose)
            implementation(libs.rssreader.coil.network.ktor3)
            implementation(libs.rssreader.androidx.lifecycle.runtime.compose)
            implementation(libs.rssreader.navigation.compose)

            // DI
            api(libs.rssreader.koin.core)
            implementation(libs.rssreader.koin.compose)

            // Networking
            implementation(libs.rssreader.ktor.core)
            implementation(libs.rssreader.ktor.logging)
            implementation(libs.rssreader.ktor.content.negotiation)
            implementation(libs.rssreader.ktor.xml)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Serialization
            implementation(libs.rssreader.kotlinx.serialization.json)
            implementation(libs.rssreader.xml.serialization)
            implementation(libs.rssreader.xml.serialization.core)

            // Storage
            implementation(libs.rssreader.multiplatform.settings)

            // Date/time
            implementation(libs.rssreader.kotlinx.datetime)

            // Logging
            implementation(libs.rssreader.napier)
        }
        androidMain.dependencies {
            implementation(libs.rssreader.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.jetbrains.rssreader"
    generateResClass = auto
}
