import org.gradle.util.GradleVersion
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
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.git.hooks.gradle.plugin)
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
    implementation(libs.gradle.nexus.publish.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.kotlinx.atomicfu.gradle.plugin)
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            val kotlinDslLanguage = if (GradleVersion.current() >= GradleVersion.version("8.0")) "1.9" else "1.4"
            languageVersion = kotlinDslLanguage
            apiVersion = kotlinDslLanguage
        }
    }
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
