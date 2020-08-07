
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://dl.bintray.com/jetbrains/kotlin-native-dependencies")
        maven("https://plugins.gradle.org/m2/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        jcenter()
    }

    dependencies {
        classpath(Plugins.kotlin)
        classpath(Plugins.dokka)
        classpath(Plugins.android)
        classpath(Plugins.atomicFu)
    }
}

plugins {
    id("de.fayard.buildSrcVersions") version "0.4.2"
}

allprojects {
    repositories {
        google()
        jcenter()
        maven("https://kotlin.bintray.com/kotlinx")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        mavenCentral()
    }

    group = project.properties["GROUP"]!!
    version = project.properties["VERSION_NAME"]!!
    if (hasProperty("SNAPSHOT") || System.getenv("SNAPSHOT") != null) {
        version = "$version-SNAPSHOT"
    }
}
