import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    id("convention.local-properties")
    id("convention.detekt")
    idea
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
    if (findProperty("project.enableSnapshots") == "true") {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

printlnCI(
    """
  CI: $CI
  SANDBOX: $SANDBOX
  isMainHost: $isMainHost
  ---
  hostIsLinux: ${HostManager.hostIsLinux}
  hostIsMac: ${HostManager.hostIsMac}
  hostIsMingw: ${HostManager.hostIsMingw}
    """.trimIndent(),
)

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

// The `afterEvaluate` wrapper is load-bearing: KMP registers its own
// `allTests` task (typed `KotlinTestReport`) later in configuration, so
// the existence check has to run after all plugins have had a chance to
// register their tasks. Migrating this off `afterEvaluate` would require
// reacting to specific plugin application via `pluginManager.withPlugin`
// for each plugin that may own these names.
afterEvaluate {
    tasks {
        if (findByName("compile") == null) {
            register("compile") {
                dependsOn(withType(AbstractCompile::class))
                group = "build"
            }
        }
        if (findByName("allTests") == null) {
            register("allTests") {
                dependsOn(withType(AbstractTestTask::class))
                group = "verification"
            }
        }
    }
}
