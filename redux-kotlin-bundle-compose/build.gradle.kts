import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("convention.publishing-mpp")
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.bundle.compose"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-bundle"))
                api(project(":redux-kotlin-compose-multimodel"))
                api(project(":redux-kotlin-compose-saveable"))
                implementation(compose.runtime)
            }
        }
    }
}
