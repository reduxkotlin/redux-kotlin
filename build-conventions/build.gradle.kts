import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions {
            // Build-conventions code is loaded by the Gradle daemon. Keep its bytecode at
            // JVM 17 so contributors on JDK 17+ can still build, even though CI uses JDK 21.
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    withType<JavaCompile>().configureEach {
        // Match the Kotlin jvmTarget above so Gradle stops flagging
        // "Inconsistent JVM Target Compatibility Between Java and Kotlin Tasks".
        options.release.set(17)
    }
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}
