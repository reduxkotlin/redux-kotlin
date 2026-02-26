plugins {
    id("convention.common")
    id("com.android.library")
}

android {
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        targetSdk = 35
    }

    publishing {
        multipleVariants {
            withSourcesJar()
            withJavadocJar()
            allVariants()
        }
    }
}
