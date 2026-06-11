import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.multimodel.granular"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-granular"))
                api(project(":redux-kotlin-multimodel"))
            }
        }
    }
}
