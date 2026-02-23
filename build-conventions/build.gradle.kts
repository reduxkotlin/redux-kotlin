import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
    if (findProperty("project.enableSnapshots") == "true") {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:_")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
    implementation("com.github.jakemarsden:git-hooks-gradle-plugin:_")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:_")
    implementation("io.github.gradle-nexus:publish-plugin:_")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:_")
    implementation("org.jetbrains.kotlinx:atomicfu-gradle-plugin:_")
}

tasks {
    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            languageVersion = "1.8" // Keep build-conventions compatible with current toolchain while Gradle 8 prep lands.
        }
    }
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
