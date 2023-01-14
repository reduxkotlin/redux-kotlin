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

refreshVersions {
  versionsPropertiesFile = rootDir.resolve("gradle/versions.properties")
  extraArtifactVersionKeyRules(rootDir.resolve("gradle/versions.rules"))
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
