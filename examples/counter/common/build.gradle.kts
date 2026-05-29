plugins {
    id("convention.control")
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js {
        useCommonJs()
        browser()
        binaries.executable()
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedCounter"
            isStatic = true
            // Export the public APIs the Swift sample consumes directly,
            // so call sites like `store.subscribeFields { ... }` resolve
            // without re-exporting boilerplate.
            export(project(":redux-kotlin"))
            export(project(":redux-kotlin-granular"))
            export(project(":redux-kotlin-threadsafe"))
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":redux-kotlin"))
                api(project(":redux-kotlin-granular"))
                api(project(":redux-kotlin-threadsafe"))
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
