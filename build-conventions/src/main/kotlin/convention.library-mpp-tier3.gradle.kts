plugins {
    id("convention.library-mpp")
    id("convention.library-android")
}

kotlin {
    // https://kotlinlang.org/docs/native-target-support.html#tier-3
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    mingwX64()
    watchosDeviceArm64()
}
