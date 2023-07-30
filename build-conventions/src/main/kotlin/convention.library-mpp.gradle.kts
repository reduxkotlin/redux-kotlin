import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("convention.common")
    kotlin("multiplatform")
    id("convention.control")
}

kotlin {
    explicitApi()
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    targetHierarchy.default()

    sourceSets {
        named("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}
