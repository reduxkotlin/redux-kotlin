import de.fayard.refreshVersions.core.versionFor

plugins {
  id("com.diffplug.spotless")
}

repositories {
  mavenCentral()
  google()
  maven("https://oss.sonatype.org/content/repositories/snapshots")
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

if (project.hasProperty("SNAPSHOT") || System.getenv("SNAPSHOT") != null) {
  version = "$version-SNAPSHOT"
}

spotless {
  val ktlintSettings = mapOf(
    "indent_size" to "2",
    "continuation_indent_size" to "4",
    "disabled_rules" to "no-wildcard-imports"
  )
  kotlin {
    target("**/*.kt")
    ktlint(versionFor("version.ktlint")).userData(ktlintSettings)
  }
  kotlinGradle {
    target("**/*.kts")
    ktlint(versionFor("version.ktlint")).userData(ktlintSettings)
  }
}
