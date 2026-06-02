import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val hasAndroidSdk: Boolean = run {
    val p = rootProject.file("local.properties")
    (p.exists() && p.readText().lineSequence().any { it.trim().startsWith("sdk.dir=") }) ||
        !System.getenv("ANDROID_HOME").isNullOrBlank() || !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
}

plugins {
    id("convention.control")
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    // NOTE: do NOT put the android plugin here — it can't be conditional in plugins{}.
}
// The `kotlin { android { } }` block is provided ONLY by this plugin; apply it (gated) outside plugins{}.
if (hasAndroidSdk) apply(plugin = "com.android.kotlin.multiplatform.library")

sqldelight {
    databases.create("TaskFlowDb") {
        packageName.set("org.reduxkotlin.sample.taskflow.db")
        generateAsync.set(true) // required for the wasmJs web-worker driver
    }
}

kotlin {
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    // The AGP-9 KMP-library `android {}` accessor is only generated when the plugin sits in plugins{}.
    // Here the plugin is applied conditionally OUTSIDE plugins{}, so its accessor isn't statically
    // available; configure the registered `android` sub-extension via its typed interface below instead.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        // no iosX64 (removed in Compose 1.11.0)
        it.binaries.framework {
            baseName = "TaskFlowApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":redux-kotlin-bundle-compose"))
            implementation(project(":redux-kotlin-devtools-core"))
            implementation(project(":redux-kotlin-devtools-inapp"))
            implementation(project(":redux-kotlin-devtools-bridge"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.markdown.renderer.coil3)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json) // pending_op payload codec
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.collections.immutable)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.activity.compose) // wires the actual BackHandler
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.java)
        }
        // Kotlin's default hierarchy template provides the iosMain intermediate (iosArm64 +
        // iosSimulatorArm64). The `iosMain { }` source-set convention accessor (KGP 2.3.x) resolves
        // it lazily — `by getting` fails here because the template materializes after this block.
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.sqldelight.web.worker.driver)
            implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.0.2"))
            implementation(npm("sql.js", "1.8.0"))
            implementation(devNpm("copy-webpack-plugin", "9.1.0")) // copies sql-wasm.wasm + worker into dist
            // wasmJs needs NO explicit Ktor engine (Ktor service-loads its built-in JS engine)
        }
        jvmTest.dependencies {
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

// Configure the AGP-9 KMP-library android target via its typed extension interface. The plugin
// registers `android` as a sub-extension of the `kotlin` extension; we reach it by name with the
// typed receiver so this compiles whether or not the (conditionally applied) plugin is present.
if (hasAndroidSdk) {
    extensions.configure(KotlinMultiplatformExtension::class.java) {
        val androidExt = (this as ExtensionAware).extensions
            .getByName("android") as KotlinMultiplatformAndroidLibraryExtension
        androidExt.namespace = "org.reduxkotlin.sample.taskflow"
        androidExt.compileSdk = 36 // Compose 1.11 androidx artifacts require API 36
        androidExt.minSdk = 24
        androidExt.androidResources { enable = true } // else composeResources fallbacks don't package on Android
    }
}

compose.desktop {
    application {
        mainClass = "org.reduxkotlin.sample.taskflow.MainKt"
    }
}
