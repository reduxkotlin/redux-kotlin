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

fun ProjectDescriptor.prefixName(prefix: String) {
  name = "$prefix-$name"
}
project(":examples:counter:common").prefixName("counter")
project(":examples:counter:android").prefixName("counter")
project(":examples:todos:common").prefixName("todos")
project(":examples:todos:android").prefixName("todos")
