pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("com.gradle.develocity") version "3.19.2"
}

includeBuild("build-conventions/")

include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
    ":redux-kotlin-granular",
    ":redux-kotlin-multimodel",
    ":redux-kotlin-compose",
    ":examples:counter:common",
    ":examples:counter:android",
    ":examples:todos:common",
    ":examples:todos:android",
)

rootProject.name = "Redux-Kotlin"
