repositories {
  mavenCentral()
  maven("https://oss.sonatype.org/content/repositories/snapshots")
  google()
}

if (project.hasProperty("SNAPSHOT") || System.getenv("SNAPSHOT") != null) {
  version = "$version-SNAPSHOT"
}
