pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.51.0"
    id("com.gradle.enterprise") version "3.12.2"
}

includeBuild("../build-conventions/")
includeBuild("../")

include(
    ":counter:common",
    ":counter:android",
    ":todos:common",
    ":todos:android"
)
