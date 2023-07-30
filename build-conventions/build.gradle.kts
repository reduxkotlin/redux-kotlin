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
    implementation(libs.plugin.android)
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.git.hooks)
    implementation(libs.plugin.detekt)
    implementation(libs.plugin.nexus)
    implementation(libs.plugin.dokka)
    implementation(libs.plugin.atomicfu)
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
