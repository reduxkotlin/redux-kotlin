import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("convention.common")
    kotlin("multiplatform")
}

kotlin {
    // Track the public ABI of every published library module. Dumps live under
    // each module's `api/` dir (JVM + merged klib). `checkKotlinAbi` (aggregated
    // by the root `apiCheck` task) gates surface drift; regenerate with
    // `./gradlew apiDump` after an intentional public-API change.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // The klib ABI dump covers every native target, but CI only builds them all on
        // the main host (macOS, per convention.control); ubuntu/windows build a subset,
        // so their dump drops apple/mingw and checkKotlinAbi fails on a spurious
        // `Targets:` header diff. Validate ABI only where the full target set exists
        // (locally, or the main host on CI).
        enabled.set(!CI || isMainHost)
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
            }
        }
    }

    js {
        useCommonJs()
        // Karma's default ChromiumHeadless launcher isn't on the Windows runner and
        // can't start its sandbox on Ubuntu 24.04. The shared `karma.config.d` rewrites
        // the browser list to a single Chrome-headless `--no-sandbox` launcher (see
        // that file); the DSL's `useChromeHeadlessNoSandbox()` only appends and would
        // leave the failing ChromiumHeadless in the list.
        browser { testTask { useKarma { useConfigDirectory(rootDir.resolve("karma.config.d")) } } }
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Same Karma browser fix as the js target: force the single Chrome-headless
        // --no-sandbox launcher via the shared karma.config.d, else wasmJsBrowserTest
        // launches the default ChromiumHeadless and fails on CI.
        browser { testTask { useKarma { useConfigDirectory(rootDir.resolve("karma.config.d")) } } }
        nodejs()
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    linuxX64()
    mingwX64()
}

// Compose's wasm runtime (skiko.wasm) can't be fetched under Node, so a
// compose-dependent module's wasm tests are browser-only — `wasmJsNodeTest`
// aborts with "both async and sync fetching of the wasm failed". Disable the
// Node variant wherever the Compose plugin is applied; `wasmJsBrowserTest` keeps
// the coverage.
plugins.withId("org.jetbrains.compose") {
    tasks.matching { it.name == "wasmJsNodeTest" }.configureEach { enabled = false }
}
