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
        // Default Karma launcher is ChromiumHeadless, which isn't installed on the
        // Windows runner and can't start its sandbox on Ubuntu 24.04 (AppArmor blocks
        // unprivileged user namespaces). Chrome ships on every GitHub runner, and
        // --no-sandbox clears the Ubuntu restriction.
        browser { testTask { useKarma { useChromeHeadlessNoSandbox() } } }
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
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
