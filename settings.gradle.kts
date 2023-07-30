pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.51.0"
    id("com.gradle.enterprise") version "3.12.3"
}

includeBuild("build-conventions/")

include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
)

rootProject.name = "Redux-Kotlin"
