@file:Suppress("UnstableApiUsage")

plugins {
    id("convention.control")
    id("com.android.application")
}

android {
    namespace = "org.reduxkotlin.example.todos"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.reduxkotlin.example.todos"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources.excludes.add("META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    implementation(project(":examples:todos:common"))
    implementation(project(":redux-kotlin-concurrent"))
    // DevTools ships only in debug builds (see src/debug vs src/release DevToolsEnhancer.kt).
    debugImplementation(project(":redux-kotlin-devtools-core"))
}
