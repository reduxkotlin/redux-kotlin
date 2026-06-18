plugins {
    id("convention.common")
    if (System.getenv("CI") == null) id("convention.git-hooks")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}
// Local/CI fallback: skip Develocity test distribution websocket check unless a server is configured.
tasks.matching { it.name == "testDistributionWebSocketCheck" }.configureEach {
    onlyIf {
        providers.gradleProperty("develocity.server").orNull != null ||
            providers.gradleProperty("gradle.enterprise.url").orNull != null ||
            System.getenv("DEVELOCITY_SERVER") != null ||
            System.getenv("GRADLE_ENTERPRISE_URL") != null
    }
}

// Repo-wide ABI dump/check aliases over the per-module Kotlin ABI-validation
// tasks (enabled in convention.mpp-loved). `matching` is lazy, so modules
// without ABI validation (examples) are simply skipped.
tasks.register("apiDump") {
    group = "verification"
    description = "Regenerate the public-API (ABI) dumps for all library modules."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "updateKotlinAbi" } })
}
tasks.register("apiCheck") {
    group = "verification"
    description = "Verify the public API of all library modules matches the committed ABI dumps."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "checkKotlinAbi" } })
}

// Compose's iOS UI test binaries can't link on the CI Xcode toolchain: compose
// ui-uikit auto-links Apple's private 'UIUtilities' framework, which the linker
// can't find ("Undefined symbols for architecture arm64"), failing
// linkDebugTestIosSimulatorArm64 for every module that renders Compose UI in
// tests (devtools-inapp-noop, -ui, the taskflow sample, …). Those UI tests run on
// the JVM, so disable the iOS test build wherever the Compose plugin is applied;
// the iOS *main* targets still compile and publish.
subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("org.jetbrains.compose")) {
            listOf(
                "linkDebugTestIosSimulatorArm64",
                "linkDebugTestIosArm64",
                "iosSimulatorArm64Test",
            ).forEach { taskName -> tasks.findByName(taskName)?.enabled = false }
        }
    }
}
