plugins {
    id("convention.library-mpp")
    id("convention.library-android")
}

kotlin {
    jvmToolchain(11)
    jvm()
    androidTarget()
    js(IR) {
        useCommonJs()
        browser { testTask(Action { useKarma() }) }
        nodejs { }
    }

    sourceSets {
        named("jvmTest") {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        named("androidUnitTest") {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        named("jsTest") {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
