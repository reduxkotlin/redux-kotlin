import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("convention.control")
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Desktop-only for now: the wasmJs web viewer was removed because the server had no
    // viewer-facing fanout (it only ingested); it returns when a broadcast path exists.
    // Pin bytecode to 17 (convention.control sets no jvmTarget) so consumers compiled against a
    // JDK 17 toolchain — e.g. redux-kotlin-devtools-cli — can load these classes on a 17 JVM.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":redux-kotlin-devtools-ui"))
            implementation(project(":redux-kotlin-devtools-core"))
            implementation(project(":redux-kotlin-devtools-bridge"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
        }
        jvmTest.dependencies {
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.reduxkotlin.devtools.monitor.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ReduxKotlinDevTools"
            // Compose packaging wants plain MAJOR.MINOR.PATCH — strip any -SNAPSHOT suffix.
            val distVersion = project.version.toString().substringBefore("-")
            packageVersion = distVersion
            macOS {
                // Dmg additionally requires MAJOR > 0; lift 0.x.y until the project reaches 1.0.
                dmgPackageVersion = if (distVersion.startsWith(
                        "0.",
                    )
                ) {
                    "1." + distVersion.removePrefix("0.")
                } else {
                    distVersion
                }
            }
        }
    }
}
