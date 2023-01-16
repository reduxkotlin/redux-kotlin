import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import util.targetGroup

plugins {
    id("convention.common")
    kotlin("multiplatform")
}

kotlin {
    js {
        useCommonJs()
        browser { testTask { useKarma() } }
        nodejs()
    }
    targetGroup(
        name = "jvmCommon",
        mainSourceSetTarget = "commonMain",
        testSourceSetTarget = "commonTest",
        jvm(),
    )
    val (nativeMain, nativeTest) = targetGroup<KotlinNativeTarget>(
        name = "native",
        mainSourceSetTarget = "commonMain",
        testSourceSetTarget = "commonTest",
    )
    val (appleMain, appleTest) = targetGroup<KotlinNativeTarget>(
        name = "apple",
        mainSourceSetTarget = nativeMain,
        testSourceSetTarget = nativeTest,
    )
    targetGroup(
        name = "ios",
        mainSourceSetTarget = appleMain,
        testSourceSetTarget = appleTest,
        iosArm32(),
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    )
    targetGroup(
        name = "tvos",
        mainSourceSetTarget = appleMain,
        testSourceSetTarget = appleTest,
        tvosArm64(),
        tvosX64(),
        tvosSimulatorArm64(),
    )
    targetGroup(
        name = "watchos",
        mainSourceSetTarget = appleMain,
        testSourceSetTarget = appleTest,
        watchosArm32(),
        watchosArm64(),
        watchosX64(),
        watchosX86(),
        watchosSimulatorArm64()
    )
    targetGroup(
        name = "macos",
        mainSourceSetTarget = appleMain,
        testSourceSetTarget = appleTest,
        macosX64(),
        macosArm64(),
    )
    targetGroup(
        name = "mingw",
        mainSourceSetTarget = nativeMain,
        testSourceSetTarget = nativeTest,
        mingwX64(),
    )
    targetGroup(
        name = "linux",
        mainSourceSetTarget = nativeMain,
        testSourceSetTarget = nativeTest,
        linuxX64(),
    )
}
