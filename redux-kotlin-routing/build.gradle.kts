import util.hasAndroidSdk

plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
}

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.routing"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                api(project(":redux-kotlin-multimodel"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":redux-kotlin-granular"))
                implementation(project(":redux-kotlin-threadsafe"))
            }
        }
    }
}
