import util.jvmCommonTest

plugins {
    id("convention.library-mpp-all")
    id("convention.publishing-mpp")
}

kotlin {
    androidLibrary {
        namespace = "org.reduxkotlin"
    }

    sourceSets {
        jvmCommonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
