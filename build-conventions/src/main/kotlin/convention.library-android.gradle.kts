plugins {
  id("convention.common")
  id("com.android.library")
}

android {
  compileSdk = 33
  defaultConfig {
    minSdk = 21
    targetSdk = 33
//    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    publishing {
      multipleVariants {
        withSourcesJar()
        withJavadocJar()
        allVariants()
      }
    }
  }
}
