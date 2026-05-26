@file:Suppress("UnstableApiUsage")

plugins {
    id("convention.control")
    id("com.android.application")
}

android {
    namespace = "org.reduxkotlin.example.counter"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.reduxkotlin.example.counter"
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
    implementation(project(":examples:counter:common"))
    implementation(project(":redux-kotlin-threadsafe"))
}
