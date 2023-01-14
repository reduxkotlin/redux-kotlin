plugins {
  id("convention.common")
  id("io.github.gradle-nexus.publish-plugin")
}

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl by uri("https://oss.sonatype.org/service/local/")
      snapshotRepositoryUrl by uri("https://oss.sonatype.org/content/repositories/snapshots/")
      val checkProp = { pName: String ->
        val exists = findProperty("sonatypeUsername")?.toString()?.takeIf(String::isNotBlank)
          ?.let { "EXISTS" } ?: "MISSING"
        printlnCI("$pName: $exists")
      }
      checkProp("sonatypeUsername")
      checkProp("sonatypePassword")
    }
  }
}
