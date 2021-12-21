plugins {
  id("de.fayard.refreshVersions") version "0.30.1"
  id("com.gradle.enterprise") version "3.8"
}

refreshVersions { extraArtifactVersionKeyRules(file("versions.rules")) }

rootProject.name = "Redux-Kotlin"

include(
  ":redux-kotlin",
  ":redux-kotlin-threadsafe",
  ":redux-kotlin-compose",
  ":examples:counter:common",
  ":examples:counter:android",
  ":examples:todos:common",
  ":examples:todos:android",
)

fun includePrefixed(prefix: String, vararg projectPaths: String) {
  projectPaths.forEach {
    include(it)
    project(it).apply { name = "$prefix-$name" }
  }
}
includePrefixed("counter", ":examples:counter:common", ":examples:counter:android")
includePrefixed("todos", ":examples:todos:common", ":examples:todos:android")
