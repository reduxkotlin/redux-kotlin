import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.kotlin.konan.target.HostManager
import util.Git

plugins {
    id("convention.common")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
}

// The `androidMain` source set shares files with `jvmCommonMain` via `kotlin.srcDir`
// (see `convention.library-mpp-loved`). Dokka V2's pre-generation validity check
// rejects a single .kt file appearing in two source sets, so suppress the android
// source set entirely — its docs would be duplicates of jvmCommonMain anyway.
dokka {
    dokkaSourceSets.matching { it.name.startsWith("android") }.configureEach {
        suppress.set(true)
    }
}

tasks {
    val dokkaPublicationHtml = named<DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml")
    register<Jar>("javadocJar") {
        dependsOn(dokkaPublicationHtml)
        archiveClassifier.set("javadoc")
        from(dokkaPublicationHtml.flatMap { it.outputDirectory })
    }
    withType<Jar> {
        manifest {
            attributes += sortedMapOf(
                "Built-By" to System.getProperty("user.name"),
                "Build-Jdk" to System.getProperty("java.version"),
                "Implementation-Version" to project.version,
                "Created-By" to "${GradleVersion.current()}",
                "Created-From" to "${Git.headCommitHash}",
            )
        }
    }
    val cleanMavenLocal by registering {
        group = "build"
        doLast {
            val m2Repo = file("${System.getProperty("user.home")}/.m2/repository")
            val groupRepo = file("$m2Repo/${project.group.toString().replace(".", "/")}")
            publishing.publications.filterIsInstance<MavenPublication>().forEach {
                groupRepo.resolve(it.artifactId).deleteRecursively()
            }
        }
    }
    named("clean") {
        dependsOn(cleanMavenLocal)
    }
    register("publishToLocal") {
        description = "Publishes all packages to local maven repository at rootDir/build/localMaven"
        dependsOn("publishAllPublicationsToLocalRepository")
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

val isMainHost = HostManager.simpleOsName().equals("${project.findProperty("project.mainOS")}", true)

publishing {
    publications {
        val ghOwnerId: String = project.findProperty("gh.owner.id")!!.toString()
        val ghOwnerName: String = project.findProperty("gh.owner.name")!!.toString()
        val ghOwnerOrganization: String = project.findProperty("gh.owner.organization")!!.toString()
        val ghOwnerOrganizationUrl: String = project.findProperty("gh.owner.organization.url")!!.toString()
        withType<MavenPublication> {
            // tasks.named() returns a TaskProvider (lazy); `tasks["..."]`
            // forces eager realization.
            artifact(tasks.named("javadocJar"))
            pom {
                name by project.name
                url by "https://github.com/$ghOwnerId/${rootProject.name}"
                description.set(project.description ?: project.name)

                licenses {
                    license {
                        name by "The Apache License, Version 2.0"
                        url by "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution by "repo"
                    }
                }

                developers {
                    developer {
                        id by ghOwnerId
                        name by ghOwnerName
                        organization by ghOwnerOrganization
                        organizationUrl by ghOwnerOrganizationUrl
                    }
                }

                scm {
                    connection by "scm:git:git@github.com:$ghOwnerId/${rootProject.name.lowercase()}.git"
                    url by "https://github.com/$ghOwnerId/${rootProject.name.lowercase()}"
                    tag.set(Git.headCommitHash ?: "HEAD")
                }
            }
        }

        repositories {
            maven("https://maven.pkg.github.com/$ghOwnerId/${rootProject.name}") {
                name = "GitHub"
                credentials {
                    username = System.getenv("GH_USERNAME")
                    password = System.getenv("GH_PASSWORD")
                }
            }
            maven(rootProject.layout.buildDirectory.dir("localMaven").map { it.asFile.toURI() }) {
                name = "Local"
            }
        }
    }
}
