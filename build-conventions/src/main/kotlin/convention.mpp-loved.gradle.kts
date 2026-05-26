import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("convention.common")
    kotlin("multiplatform")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
            }
        }
    }

    js {
        useCommonJs()
        browser { testTask { useKarma() } }
        nodejs()
    }
    jvm()

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosX64()
    macosArm64()
    linuxX64()
    mingwX64()
}
