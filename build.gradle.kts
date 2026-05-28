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
