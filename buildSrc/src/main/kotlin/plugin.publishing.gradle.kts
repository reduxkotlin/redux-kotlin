plugins {
  id("plugin.base")
  `maven-publish`
  signing
  id("org.jetbrains.dokka")
}

val isReleaseBuild by lazy {
  !version.toString().contains("SNAPSHOT")
}

val releaseRepositoryUrl by lazy {
  findProperty("RELEASE_REPOSITORY_URL")?.toString() ?: "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
}

val snapshotRepositoryUrl by lazy {
  findProperty("SNAPSHOT_REPOSITORY_URL")?.toString() ?: "https://oss.sonatype.org/content/repositories/snapshots/"
}

val repositoryUsername by lazy {
  findProperty("SONATYPE_NEXUS_USERNAME")?.toString() ?: System.getenv("SONATYPE_NEXUS_USERNAME") ?: ""
}

val repositoryPassword by lazy {
  findProperty("SONATYPE_NEXUS_PASSWORD")?.toString() ?: System.getenv("SONATYPE_NEXUS_PASSWORD") ?: ""
}

signing {
  isRequired = isReleaseBuild // && gradle.taskGraph.hasTask("uploadArchives")
//    def signingKey = findProperty("GPG_SECRET") ?: System.getenv("GPG_SECRET") ?: ""
//    def signingPassword = findProperty("GPG_SIGNING_PASSWORD") ?: System.getenv("GPG_SIGNING_PASSWORD") ?: ""
//    useInMemoryPgpKeys(signingKey, signingPassword)
//    sign(publishing.publications)
}

tasks {
  val javadocJar by registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml)
  }

  publishing {
    publications.withType<MavenPublication> {
      artifact(javadocJar)
      pom {
        description.set(findProperty("POM_DESCRIPTION")?.toString())
        name.set(findProperty("POM_NAME")?.toString())
        url.set(findProperty("POM_URL")?.toString())
        licenses {
          license {
            name.set(findProperty("POM_LICENCE_NAME")?.toString())
            url.set(findProperty("POM_LICENCE_URL")?.toString())
            distribution.set(findProperty("POM_LICENCE_DIST")?.toString())
          }
        }
        scm {
          url.set(findProperty("POM_LICENCE_URL")?.toString())
          connection.set(findProperty("POM_SCM_CONNECTION")?.toString())
          developerConnection.set(findProperty("POM_SCM_DEV_CONNECTION")?.toString())
        }
        developers {
          developer {
            id.set(findProperty("POM_DEVELOPER_ID")?.toString())
            name.set(findProperty("POM_DEVELOPER_NAME")?.toString())
          }
        }
      }
    }

    repositories {
      maven(if (isReleaseBuild) releaseRepositoryUrl else snapshotRepositoryUrl) {
        credentials {
          username = repositoryUsername
          password = repositoryPassword
        }
      }
      maven("file://${rootProject.buildDir}/localMaven") {
        name = "test"
      }
    }
  }
}
