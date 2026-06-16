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
        enabled.set(true)
        // CI only builds every native target on the main host (macOS); ubuntu/windows
        // build a subset, so without this their klib dump would drop the unbuilt
        // targets and `checkKotlinAbi` would fail on a spurious `Targets:` header diff.
        // Infer the missing targets from the committed reference so the check is
        // host-independent.
        klib {
            keepUnsupportedTargets.set(true)
        }
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

    // NOTE: no linuxX64()/mingwX64(). compose.foundation/material3 publish no
    // linux/mingw klibs in Compose Multiplatform 1.11, so modules consuming them
    // (e.g. -ui, -inapp) can't target the desktop-native platforms. This is the
    // only difference from `convention.mpp-loved`.
}
