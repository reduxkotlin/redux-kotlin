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
  targetGroup(
    name = "androidNative",
    mainSourceSetTarget = nativeMain,
    testSourceSetTarget = nativeTest,
    androidNativeArm32(),
    androidNativeArm64(),
    androidNativeX64(),
    androidNativeX86(),
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
    watchosDeviceArm64(),
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
    mingwX86(),
  )
  targetGroup(
    name = "linux",
    mainSourceSetTarget = nativeMain,
    testSourceSetTarget = nativeTest,
    linuxArm32Hfp(),
    linuxArm64(),
    linuxMips32(),
    linuxMipsel32(),
    linuxX64(),
  )
}
