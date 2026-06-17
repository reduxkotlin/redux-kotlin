plugins {
    id("convention.control")
    kotlin("multiplatform")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    js {
        useCommonJs()
        // Force the single Chrome-headless --no-sandbox Karma launcher (shared
        // karma.config.d) so jsBrowserTest works on CI; the default ChromiumHeadless
        // isn't on the Windows runner and can't sandbox on Ubuntu 24.04.
        browser { testTask { useKarma { useConfigDirectory(rootDir.resolve("karma.config.d")) } } }
        binaries.executable()
    }
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":redux-kotlin"))
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
