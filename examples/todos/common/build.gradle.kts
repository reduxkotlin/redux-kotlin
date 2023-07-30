plugins {
    id("convention.control")
    kotlin("multiplatform")
}

kotlin {
    iosArm32()
    iosArm64()
    iosX64()
    js(IR) {
        useCommonJs()
        browser()
        binaries.executable()
    }
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.reduxkotlin:redux-kotlin:_")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        named("jvmTest") {
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
