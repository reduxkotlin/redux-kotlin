plugins {
    id("convention.publishing-jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

description = "Headless redux-kotlin Compose renderer: f(state) -> PNG, with golden-image " +
    "diffing and an HTML dashboard. Experimental — exempt from semver."

// Pin the compile JDK to 17 — matches the repo convention (JvmTarget.JVM_17) and the
// redux-kotlin-devtools-cli fix (Compose 1.11.x + Skiko load fine at 17). Do NOT raise to 21.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // `api` for every dependency whose types surface in the public API: the scene
    // DSL exposes `@Composable` (compose), `snapshotCommand(): CliktCommand` (clikt),
    // and serializable manifest/state types (serialization). Consumers authoring
    // scenes need these on their compile classpath.
    api(project(":redux-kotlin"))
    api(libs.clikt)
    api(libs.kotlinx.serialization.json)
    api(compose.runtime)
    api(compose.ui)
    api(compose.foundation)
    api(compose.material3)
    // Host-agnostic desktop/Skiko API for compiling the renderer. We deliberately do
    // NOT depend on `compose.desktop.currentOs` here: that bakes the publisher's host
    // skiko (e.g. desktop-jvm-macos-arm64) into the published POM. Consumers building a
    // desktop app already bring `currentOs`, which supplies the native runtime.
    implementation(compose.desktop.common)
    // Tests rasterize on this host, so they need the host-specific native runtime.
    testImplementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}
