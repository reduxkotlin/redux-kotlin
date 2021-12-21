plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
  mavenCentral()
  google()
  maven("https://oss.sonatype.org/content/repositories/snapshots")
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
  implementation("org.jetbrains.compose:compose-gradle-plugin:_")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
  implementation("org.jetbrains.dokka:dokka-gradle-plugin:_")
  implementation("org.jetbrains.kotlinx:atomicfu-gradle-plugin:_")
  implementation("com.android.tools.build:gradle:_")
  implementation("com.diffplug.spotless:spotless-plugin-gradle:_")
}
