@file:Suppress("UnstableApiUsage")

plugins {
    id("convention.control")
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

android {
    namespace = "org.reduxkotlin.example.counter"
    compileSdk = 33
    defaultConfig {
        applicationId = "org.reduxkotlin.example.todos"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
    packagingOptions {
        resources.excludes.add("META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:_")
    implementation(project(":counter:common"))
    implementation("org.reduxkotlin:redux-kotlin-threadsafe:_")
}
