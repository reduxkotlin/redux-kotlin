pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.19.2"
}

includeBuild("build-conventions/")

include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
)

rootProject.name = "Redux-Kotlin"
