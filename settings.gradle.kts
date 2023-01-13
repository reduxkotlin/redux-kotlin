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
includeBuild("build-conventions/")
refreshVersions {
  versionsPropertiesFile = rootDir.resolve("gradle/versions.properties")
  extraArtifactVersionKeyRules(rootDir.resolve("gradle/versions.rules"))
}

include(
  ":redux-kotlin",
//    ":redux-kotlin-threadsafe",
//    ":examples:counter:common",
//    ":examples:counter:android",
//    ":examples:todos:common",
//    ":examples:todos:android"
)

rootProject.name = "Redux-Kotlin"
