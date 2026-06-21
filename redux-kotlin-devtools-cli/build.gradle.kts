plugins {
    id("convention.common")
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Pin the compile JDK (not just the bytecode target) so the build is deterministic regardless of
// the developer's default Java, and so we never accidentally compile against a >17 JDK API. The
// foojay resolver (settings.gradle.kts) auto-downloads JDK 17 when it isn't already installed.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":redux-kotlin-devtools-core"))
    implementation(project(":redux-kotlin-devtools-bridge"))
    implementation(project(":redux-kotlin-devtools-standalone"))
    implementation(project(":redux-kotlin-devtools-ui"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.ui)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
