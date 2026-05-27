import dev.detekt.gradle.Detekt

plugins {
    id("dev.detekt")
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.3")
}

detekt {
    config.from(rootDir.resolve("gradle/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom("src/", "*.kts")
}

tasks {
    if (project == rootProject) {
        register("detektAll", Detekt::class) {
            description = "Run Detekt for all modules"
            config.from(project.detekt.config)
            buildUponDefaultConfig = project.detekt.buildUponDefaultConfig
            setSource(files(projectDir))
        }
    }
    afterEvaluate {
        withType<Detekt> {
            parallel = true
            reports {
                // observe findings in your browser with structure and code snippets
                html.required.set(true)
                // checkstyle-like format mainly for Jenkins-style integrations
                // (renamed from `xml` in detekt 2.0).
                checkstyle.required.set(true)
                // standardized SARIF format (https://sarifweb.azurewebsites.net/) for GitHub Code Scanning.
                sarif.required.set(true)
                // markdown report (renamed from `md` in detekt 2.0).
                markdown.required.set(true)
            }
            include("**/*.kt", "**/*.kts")
            // `.claude/` holds Claude Code harness worktrees / caches; never
            // scan them. `**/build` is the Gradle output dir.
            exclude("**/build", "scripts/", ".claude/", "**/.claude/")
        }
    }
}
