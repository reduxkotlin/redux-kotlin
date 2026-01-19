plugins {
    id("convention.common")
    id("com.android.library")
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 34
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
