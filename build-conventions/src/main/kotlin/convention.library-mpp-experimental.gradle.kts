import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    id("convention.library-mpp")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasm {
//        browser { testTask(Action { useKarma { useChromiumHeadless() } }) }
//        nodejs { } testing is borked for now
        d8 { }
    }

    sourceSets {
        named("wasmTest") {
            dependencies {
                implementation(kotlin("test-wasm"))
            }
        }
    }
}
