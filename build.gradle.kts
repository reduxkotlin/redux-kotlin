plugins {
    id("convention.common")
    id("convention.publishing-nexus")
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
