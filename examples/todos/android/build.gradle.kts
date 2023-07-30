@file:Suppress("UnstableApiUsage")

plugins {
    id("convention.control")
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

android {
    namespace = "org.reduxkotlin.example.todos"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources.excludes.add("META-INF/*.kotlin_module")
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    implementation(project(":todos:common"))
    implementation("org.reduxkotlin:redux-kotlin-threadsafe")
}
