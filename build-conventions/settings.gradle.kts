plugins {
    id("de.fayard.refreshVersions") version "0.51.0"
    id("com.gradle.enterprise") version "3.16.2"
}

refreshVersions {
    versionsPropertiesFile = rootDir.resolve("gradle/versions.properties")
    extraArtifactVersionKeyRules(rootDir.resolve("gradle/versions.rules"))
}
