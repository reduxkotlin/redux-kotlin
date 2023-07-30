plugins {
    id("convention.library-mpp")
    id("convention.library-android")
}

kotlin {
    // https://kotlinlang.org/docs/native-target-support.html#tier-2
    linuxArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()
    iosArm64()
}
