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
    // Auto-provisions a matching JDK for Gradle Java toolchains (e.g. the CLI's jvmToolchain(17)),
    // so the build JDK is deterministic regardless of the developer's default Java.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

includeBuild("build-conventions/")

include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
    ":redux-kotlin-concurrent",
    ":redux-kotlin-thunk",
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
    ":redux-kotlin-compose-saveable",
    ":redux-kotlin-devtools-core",
    ":redux-kotlin-devtools-bridge",
    ":redux-kotlin-devtools-remote",
    ":redux-kotlin-devtools-inapp",
    ":redux-kotlin-devtools-inapp-noop",
    ":redux-kotlin-devtools-ui",
    ":redux-kotlin-devtools-standalone",
    ":redux-kotlin-devtools-cli",
    ":redux-kotlin-snapshot",
    ":redux-kotlin-cli",
    ":redux-kotlin-cli-dist",
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
