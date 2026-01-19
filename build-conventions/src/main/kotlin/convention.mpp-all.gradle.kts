import util.targetGroup

plugins {
    id("convention.mpp-loved")
}

kotlin {
    val nativeMain by sourceSets.getting
    val nativeTest by sourceSets.getting
    targetGroup(
        name = "androidNative",
        mainSourceSetTarget = nativeMain,
        testSourceSetTarget = nativeTest,
        androidNativeArm32(),
        androidNativeArm64(),
        androidNativeX64(),
        androidNativeX86(),
    )
    // mingwX86() removed - deprecated in Kotlin 2.x
    targetGroup(
        name = "linux",
        mainSourceSetTarget = nativeMain,
        testSourceSetTarget = nativeTest,
        linuxArm64(),
    )
    targetGroup(
        name = "watchos",
        mainSourceSetTarget = "appleMain",
        testSourceSetTarget = "appleTest",
        watchosDeviceArm64(),
    )
}
