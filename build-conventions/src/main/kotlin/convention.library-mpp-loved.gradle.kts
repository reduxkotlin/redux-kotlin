import util.jvmCommonTest
import util.withName

plugins {
    id("convention.mpp-loved")
    id("convention.control")
    id("convention.library-android") apply false
}

val hasAndroidSdk: Boolean = run {
    val localProps = rootProject.file("local.properties")
    val hasSdkInLocalProperties = localProps.exists() && localProps.readText().lineSequence().any {
        it.trim().startsWith("sdk.dir=") && it.substringAfter("sdk.dir=").isNotBlank()
    }
    val hasSdkInEnv =
        !System.getenv("ANDROID_HOME").isNullOrBlank() ||
            !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
    hasSdkInLocalProperties || hasSdkInEnv
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
        androidLibrary {
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
