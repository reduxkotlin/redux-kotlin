plugins {
    id("convention.common")
    id("com.android.library")
}

android {
    compileSdk = 33
    defaultConfig {
        minSdk = 21
        publishing {
            multipleVariants {
                withSourcesJar()
                withJavadocJar()
                allVariants()
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
