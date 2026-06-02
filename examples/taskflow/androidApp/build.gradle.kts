plugins {
    id("convention.control") // brings repositories (mavenCentral/google) + host gating
    id("com.android.application") // version comes from the convention build's classpath
    // AGP 9 has built-in Kotlin support; the separate kotlin("android") plugin is rejected.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "org.reduxkotlin.sample.taskflow.app"
    compileSdk = 36 // Compose 1.11 requires API 36
    defaultConfig {
        applicationId = "org.reduxkotlin.sample.taskflow"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":examples:taskflow:composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(compose.runtime)
    // DevTools is wired only in debug builds — installDebugTooling() in src/debug installs the
    // store-enhancer hook; the release source set is a no-op and never links these artifacts.
    // multimodel supplies the ModelState type the debug enhancer is generic over (composeApp
    // depends on the bundle via implementation, so it isn't exposed transitively here).
    debugImplementation(project(":redux-kotlin-devtools-core"))
    debugImplementation(project(":redux-kotlin-multimodel"))
}
