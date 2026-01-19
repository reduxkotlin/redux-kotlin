plugins {
    id("de.fayard.refreshVersions") version "0.60.5"
}

refreshVersions {
    versionsPropertiesFile = rootDir.parentFile.resolve("gradle/versions.properties")
    extraArtifactVersionKeyRules(rootDir.parentFile.resolve("gradle/versions.rules"))
}
