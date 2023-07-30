plugins {
    id("convention.atomicfu")
    id("convention.library-mpp-tier0")
    id("convention.library-mpp-tier1")
    id("convention.library-mpp-tier2")
    id("convention.library-mpp-tier3")
    id("convention.publishing-mpp")
}
android {
    namespace = "org.reduxkotlin.threadsafe"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(libs.atomicfu)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        androidUnitTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
