plugins {
    id("convention.common")
    kotlin("jvm")
    application
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Pin the compile JDK to 17 — matches the repo convention (JvmTarget.JVM_17) and the
// redux-kotlin-devtools-cli fix (Compose 1.11.x + Skiko load fine at 17). Do NOT raise to 21.
kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "rk-snapshot"
    mainClass.set("org.reduxkotlin.snapshot.cli.MainKt")
}

dependencies {
    api(project(":redux-kotlin"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}
