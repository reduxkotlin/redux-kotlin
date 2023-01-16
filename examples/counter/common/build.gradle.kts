plugins {
    id("convention.control")
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR) {
        useCommonJs()
        browser()
        binaries.executable()
    }

    iosArm64()
    iosX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.reduxkotlin:redux-kotlin")
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
