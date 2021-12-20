plugins {
    id("de.fayard.refreshVersions") version "0.23.0"
////                            # available:"0.30.0"
    id("com.gradle.enterprise") version "3.8"
}

refreshVersions { extraArtifactVersionKeyRules(file("versions.rules")) }

rootProject.name = "Redux-Kotlin"

include(
    ":redux-kotlin",
    ":redux-kotlin-threadsafe",
    ":redux-kotlin-compose",
//    ":examples:counter:common",
//    ":examples:counter:android",
//    ":examples:todos:common",
//    ":examples:todos:android",
)
