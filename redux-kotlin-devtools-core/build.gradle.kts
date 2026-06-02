plugins {
    id("convention.library-mpp-loved")
    id("convention.publishing-mpp")
    alias(libs.plugins.kotlin.serialization)
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

kotlin {
    if (hasAndroidSdk) {
        android {
            namespace = "org.reduxkotlin.devtools"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        named("jvmCommonMain") {
            dependencies {
                implementation(libs.kotlin.reflect)
            }
        }
        if (hasAndroidSdk) {
            named("androidMain") {
                dependencies {
                    implementation(libs.kotlin.reflect)
                }
            }
        }
    }
}
