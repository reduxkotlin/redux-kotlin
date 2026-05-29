import com.vanniktech.maven.publish.JavaPlatform
import util.Git

plugins {
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

val ghOwnerId: String = project.findProperty("gh.owner.id")!!.toString()
val ghOwnerName: String = project.findProperty("gh.owner.name")!!.toString()
val ghOwnerOrganization: String = project.findProperty("gh.owner.organization")!!.toString()
val ghOwnerOrganizationUrl: String = project.findProperty("gh.owner.organization.url")!!.toString()

mavenPublishing {
    configure(JavaPlatform())
    coordinates(project.group.toString(), project.name, project.version.toString())
    publishToMavenCentral()
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
