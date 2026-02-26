plugins {
    id("convention.common")
    id("convention.publishing-nexus")
    if (System.getenv("CI") == null) id("convention.git-hooks")
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
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
