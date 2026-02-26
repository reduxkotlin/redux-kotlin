import util.targetGroup

plugins {
    id("convention.mpp-loved")
}

kotlin {
    val nativeMain by sourceSets.getting
    val nativeTest by sourceSets.getting

    targetGroup(
        name = "linux",
        mainSourceSetTarget = nativeMain,
        testSourceSetTarget = nativeTest,
        linuxArm64(),
    )
}
