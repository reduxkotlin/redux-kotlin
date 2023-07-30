import util.jvmCommonTest

plugins {
    id("convention.library-mpp-all")
    id("convention.publishing-mpp")
}

android {
    namespace = "org.reduxkotlin"
}

kotlin {
    sourceSets {
        jvmCommonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
