plugins {
  id("plugin.base")
  kotlin("multiplatform")
  id("kotlinx-atomicfu")
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation("org.jetbrains.kotlinx:atomicfu:_")
      }
    }
  }
}
