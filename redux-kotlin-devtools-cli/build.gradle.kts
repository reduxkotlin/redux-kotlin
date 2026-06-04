import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.common")
    kotlin("jvm")
    application
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "rk-devtools"
    mainClass.set("org.reduxkotlin.devtools.cli.MainKt")
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
