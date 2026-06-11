import util.hasAndroidSdk
import util.jvmCommonTest
import util.withName

plugins {
    id("convention.mpp-loved-composeui")
    id("convention.control")
    id("convention.library-android") apply false
}

if (hasAndroidSdk) {
    apply(plugin = "convention.library-android")
}

kotlin {
    explicitApi()

    android {
        compileSdk = 35
    }

    if (hasAndroidSdk) {
        android {
            minSdk = 21
        }
    }

    sourceSets {
        named("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                val jvmCommonMain by getting
                kotlin.srcDir(jvmCommonMain.kotlin)
                resources.srcDir(jvmCommonMain.resources)
            }
            withName("androidUnitTest") {
                val jvmCommonTest by getting
                kotlin.srcDir(jvmCommonTest.kotlin)
                resources.srcDir(jvmCommonTest.resources)
            }
            withName("androidHostTest") {
                val jvmCommonTest by getting
                kotlin.srcDir(jvmCommonTest.kotlin)
                resources.srcDir(jvmCommonTest.resources)
            }
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
