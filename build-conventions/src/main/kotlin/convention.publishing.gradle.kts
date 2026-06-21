import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import util.Git

plugins {
    id("convention.common")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
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

val ghOwnerId: String = project.findProperty("gh.owner.id")!!.toString()
val ghOwnerName: String = project.findProperty("gh.owner.name")!!.toString()
val ghOwnerOrganization: String = project.findProperty("gh.owner.organization")!!.toString()
val ghOwnerOrganizationUrl: String = project.findProperty("gh.owner.organization.url")!!.toString()

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        ),
    )
    coordinates(project.group.toString(), project.name, project.version.toString())

    // Targets the Central Publisher Portal (the OSSRH replacement). automaticRelease
    // = true publishes the deployment automatically once validation passes, instead of
    // leaving it staged for a manual Drop/Publish in the Portal UI.
    publishToMavenCentral(automaticRelease = true)

    // Only sign when a key is configured. Locally (publishToLocal / publishToMavenLocal)
    // no key is present and Maven Central doesn't require signatures for local repos, so
    // skipping avoids "no configured signatory" failures. CI sets signingInMemoryKey.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
        description.set(project.description ?: project.name)
        url.set("https://github.com/$ghOwnerId/${rootProject.name}")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set(ghOwnerId)
                name.set(ghOwnerName)
                organization.set(ghOwnerOrganization)
                organizationUrl.set(ghOwnerOrganizationUrl)
            }
        }

        scm {
            connection.set("scm:git:git@github.com:$ghOwnerId/${rootProject.name.lowercase()}.git")
            url.set("https://github.com/$ghOwnerId/${rootProject.name.lowercase()}")
            tag.set(Git.headCommitHash ?: "HEAD")
        }
    }
}

tasks {
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
    // Offline rehearsal: publishes every module to the real Maven Local repo
    // (~/.m2). vanniktech skips signing for Maven Local, so this needs no GPG key.
    register("publishToLocal") {
        description = "Publishes all packages to the local maven repository (~/.m2)"
        dependsOn("publishToMavenLocal")
    }
}

publishing {
    repositories {
        maven("https://maven.pkg.github.com/$ghOwnerId/${rootProject.name}") {
            name = "GitHub"
            credentials {
                username = System.getenv("GH_USERNAME")
                password = System.getenv("GH_PASSWORD")
            }
        }
    }
}
