import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.bundle"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin-concurrent"))
                api(project(":redux-kotlin-registry"))
                api(project(":redux-kotlin-routing"))
                api(project(":redux-kotlin-multimodel-granular"))
            }
        }
    }
}
