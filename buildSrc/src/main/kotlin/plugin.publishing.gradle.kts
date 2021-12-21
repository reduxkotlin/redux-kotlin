plugins {
  id("plugin.base")
  `maven-publish`
  signing
  id("org.jetbrains.dokka")
}

val isReleaseBuild by lazy {
  !version.toString().contains("SNAPSHOT")
}

fun propOrEnv(key: String): String? = findProperty(key)?.toString() ?: System.getenv(key)

val releaseRepositoryUrl by lazy {
  propOrEnv("RELEASE_REPOSITORY_URL") ?: "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
}

val snapshotRepositoryUrl by lazy {
  propOrEnv("SNAPSHOT_REPOSITORY_URL") ?: "https://oss.sonatype.org/content/repositories/snapshots/"
}

val repositoryUsername: String? by lazy {
  propOrEnv("SONATYPE_NEXUS_USERNAME")
}

val repositoryPassword: String? by lazy {
  propOrEnv("SONATYPE_NEXUS_PASSWORD")
}

signing {
  isRequired = isReleaseBuild // && gradle.taskGraph.hasTask("uploadArchives")
  val signingKey: String? = propOrEnv("GPG_SECRET")
  val signingPassword: String? = propOrEnv("GPG_SIGNING_PASSWORD")
  if (signingKey != null && signingPassword != null) {
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
  }
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
        description.set(propOrEnv("POM_DESCRIPTION"))
        name.set(propOrEnv("POM_NAME"))
        url.set(propOrEnv("POM_URL"))
        licenses {
          license {
            name.set(propOrEnv("POM_LICENCE_NAME"))
            url.set(propOrEnv("POM_LICENCE_URL"))
            distribution.set(propOrEnv("POM_LICENCE_DIST"))
          }
        }
        scm {
          url.set(propOrEnv("POM_LICENCE_URL"))
          connection.set(propOrEnv("POM_SCM_CONNECTION"))
          developerConnection.set(propOrEnv("POM_SCM_DEV_CONNECTION"))
        }
        developers {
          developer {
            id.set(propOrEnv("POM_DEVELOPER_ID"))
            name.set(propOrEnv("POM_DEVELOPER_NAME"))
          }
        }
      }
    }

    repositories {
      maven(if (isReleaseBuild) releaseRepositoryUrl else snapshotRepositoryUrl) {
        name = "MavenCentral"
        credentials {
          username = repositoryUsername
          password = repositoryPassword
        }
      }
      maven("file://${rootProject.buildDir}/localMaven") {
        name = "MavenTest"
      }
    }
  }
}
