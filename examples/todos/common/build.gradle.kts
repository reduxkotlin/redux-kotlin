plugins {
    java
    kotlin("multiplatform")
}

kotlin {
    iosArm32()
    iosArm64()
    iosX64()
    js(IR) {
        binaries.executable()
        browser()

        listOf(compilations["main"], compilations["test"]).forEach {
            with(it.kotlinOptions) {
                moduleKind = "umd"
                sourceMap = true
                sourceMapEmbedSources = "always"
                metaInfo = true
            }
        }
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
                implementation("io.mockk:mockk-common:${Versions.io_mockk}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("io.mockk:mockk:${Versions.io_mockk}")

                runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation(kotlin("stdlib-js"))
            }
        }
    }
}
