pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.5"
    id("com.gradle.enterprise") version "3.12.3"
}

refreshVersions {
    versionsPropertiesFile = rootDir.resolve("gradle/versions.properties")
    extraArtifactVersionKeyRules(rootDir.resolve("gradle/versions.rules"))
}

includeBuild("build-conventions/")

include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
)

rootProject.name = "Redux-Kotlin"
