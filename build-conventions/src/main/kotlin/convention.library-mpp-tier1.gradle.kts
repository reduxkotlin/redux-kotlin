plugins {
    id("convention.library-mpp")
    id("convention.library-android")
}

kotlin {
    // https://kotlinlang.org/docs/native-target-support.html#tier-1
    linuxX64()
    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosX64()
}
