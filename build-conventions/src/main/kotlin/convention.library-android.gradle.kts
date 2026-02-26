plugins {
    id("convention.common")
    id("com.android.library")
}

android {
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        targetSdk = 35
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
