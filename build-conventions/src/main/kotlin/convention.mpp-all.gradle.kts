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
    targetGroup(
        name = "mingw",
        mainSourceSetTarget = nativeMain,
        testSourceSetTarget = nativeTest,
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
    )
    targetGroup(
        name = "watchos",
        mainSourceSetTarget = "appleMain",
        testSourceSetTarget = "appleTest",
        watchosDeviceArm64(),
    )
}
