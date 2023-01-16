import util.jvmCommonTest

plugins {
    id("convention.mpp-loved")
    id("convention.library-android")
    id("convention.control")
}

kotlin {
    explicitApi()
    android {
        if (!CI || SANDBOX || isMainHost) {
            publishLibraryVariants("release", "debug")
        }
    }

    sourceSets {
        named("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        named("androidMain") {
            val jvmCommonMain by getting
            kotlin.srcDir(jvmCommonMain.kotlin)
            resources.srcDir(jvmCommonMain.resources)
        }
        named("androidUnitTest") {
            val jvmCommonTest by getting
            kotlin.srcDir(jvmCommonTest.kotlin)
            resources.srcDir(jvmCommonTest.resources)
        }
        named("jsTest") {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        jvmCommonTest {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}
