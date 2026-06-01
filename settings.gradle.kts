pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("com.google.devtools.ksp") version "2.3.9"
    }
}

plugins {
    id("com.gradle.develocity") version "3.19.2"
}

includeBuild("build-conventions/")

include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
    ":redux-kotlin-concurrent",
    ":redux-kotlin-registry",
    ":redux-kotlin-granular",
    ":redux-kotlin-multimodel",
    ":redux-kotlin-routing",
    ":redux-kotlin-bundle",
    ":redux-kotlin-bundle-compose",
    ":redux-kotlin-bom",
    ":redux-kotlin-routing-codegen",
    ":redux-kotlin-routing-codegen-sample",
    ":redux-kotlin-compose",
    ":redux-kotlin-multimodel-granular",
    ":redux-kotlin-compose-multimodel",
    ":redux-kotlin-devtools",
    ":examples:counter:common",
    ":examples:counter:android",
    ":examples:todos:common",
    ":examples:todos:android",
    ":examples:taskflow:composeApp",
)

val hasAndroidSdk = file("local.properties").let { it.exists() && it.readText().contains("sdk.dir=") } ||
    System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) include(":examples:taskflow:androidApp")

rootProject.name = "Redux-Kotlin"
