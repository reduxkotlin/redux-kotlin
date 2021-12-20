plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.compose:compose-gradle-plugin:_")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:_")
    implementation("org.jetbrains.kotlinx:atomicfu-gradle-plugin:_")
}
