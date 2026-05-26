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

includeBuild("../build-conventions/")
// TODO(KT-52172) Uncomment once KMP properly supports composite builds
// includeBuild("../")

include(
    ":counter:common",
    ":counter:android",
    ":todos:common",
    ":todos:android"
)
