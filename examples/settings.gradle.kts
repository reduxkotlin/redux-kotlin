pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.12.6"
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
