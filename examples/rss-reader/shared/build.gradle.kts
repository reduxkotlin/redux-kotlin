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

    sourceSets {
        commonMain.dependencies {
            implementation(project(":redux-kotlin"))
            implementation(libs.rssreader.compose.runtime)
            implementation(libs.rssreader.compose.foundation)
            implementation(libs.rssreader.compose.material3)
            implementation(libs.rssreader.compose.ui)
            implementation(libs.rssreader.compose.components.resources)
            implementation(libs.rssreader.compose.ui.tooling.preview)
            implementation(libs.rssreader.coil.compose)
            implementation(libs.rssreader.coil.network.ktor3)
            implementation(libs.rssreader.androidx.lifecycle.runtime.compose)
            implementation(libs.rssreader.koin.compose)
            implementation(libs.rssreader.navigation.compose)
            implementation(libs.rssreader.material.icons.core)
            implementation(libs.rssreader.ktor.core)
            implementation(libs.rssreader.ktor.logging)
            implementation(libs.rssreader.ktor.content.negotiation)
            implementation(libs.rssreader.ktor.xml)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.rssreader.napier)
            implementation(libs.rssreader.kotlinx.serialization.json)
            implementation(libs.rssreader.multiplatform.settings)
            api(libs.rssreader.koin.core)
            implementation(libs.rssreader.kotlinx.datetime)
            implementation(libs.rssreader.xml.serialization)
            implementation(libs.rssreader.xml.serialization.core)
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
