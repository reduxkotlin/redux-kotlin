import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.control")
    id("com.android.application")
    alias(libs.plugins.rssreader.compose.multiplatform)
    alias(libs.plugins.rssreader.compose.compiler)
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    dependencies {
        implementation(project(":examples:rss-reader:shared"))
        implementation(libs.rssreader.activity.compose)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.rssreader.koin.core)
        implementation(libs.rssreader.koin.android)
        implementation(libs.rssreader.napier)
        implementation(libs.rssreader.multiplatform.settings)
        implementation(libs.rssreader.kotlinx.serialization.json)
        implementation(libs.rssreader.ktor.core)
        implementation(libs.rssreader.core.splashscreen)
    }
}

android {
    namespace = "com.github.jetbrains.rssreader"
    compileSdk = libs.versions.rssreader.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.jetbrains.rssreader"
        minSdk = libs.versions.rssreader.android.min.sdk.get().toInt()
        targetSdk = libs.versions.rssreader.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-sample"
    }

    buildTypes {
        getByName("debug") { isMinifyEnabled = false }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
