import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import util.Git

// Publishing for a single-target `kotlin("jvm")` library. Mirrors
// `convention.publishing` (which is bound to vanniktech's `KotlinMultiplatform`
// platform and cannot configure a JVM-only module) but uses `KotlinJvm` and the
// JVM ABI dump. Use this for desktop/JVM-only tools that still ship to Maven
// Central (e.g. `redux-kotlin-snapshot`).
plugins {
    id("convention.common")
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

kotlin {
    // Lock the public API: every published declaration needs an explicit visibility
    // modifier, matching the MPP library conventions.
    explicitApi()

    // Track the public ABI under `api/`. A JVM module has a single target, so the
    // host-gating the klib dump needs does not apply — always validate.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
}

val ghOwnerId: String = project.findProperty("gh.owner.id")!!.toString()
val ghOwnerName: String = project.findProperty("gh.owner.name")!!.toString()
val ghOwnerOrganization: String = project.findProperty("gh.owner.organization")!!.toString()
val ghOwnerOrganizationUrl: String = project.findProperty("gh.owner.organization.url")!!.toString()

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        ),
    )
    coordinates(project.group.toString(), project.name, project.version.toString())

    // Targets the Central Publisher Portal; automaticRelease publishes once
    // validation passes instead of leaving the deployment staged.
    publishToMavenCentral(automaticRelease = true)

    // Only sign when a key is configured (CI). Local publishing has no key and
    // Maven Local needs none, so skipping avoids "no configured signatory".
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
        // Lazily — a module's `description = …` statement runs AFTER this convention's
        // plugin block, and the root `description` gradle property would otherwise win.
        description.set(provider { project.description ?: project.name })
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
