plugins {
    id("convention.common")
    kotlin("jvm")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// Packaging-only module — NO `application` plugin (it clashes with Compose's `run` task).
// Depends on :redux-kotlin-cli for the rk entry point + full runtime classpath (Compose/Skiko
// arrive transitively). createDistributable bundles that classpath + a jlink JRE.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":redux-kotlin-cli"))
}

// Self-contained per-OS app-image with a bundled (jlink-minimized) JRE — `rk` runs with no
// system Java. createDistributable produces build/compose/binaries/main/app/rk[.app].
compose.desktop {
    application {
        mainClass = "org.reduxkotlin.cli.MainKt"
        nativeDistributions {
            // jpackage rejects -SNAPSHOT / pre-release qualifiers and wants MAJOR>0 on macOS.
            packageName = "rk"
            packageVersion = project.version.toString().substringBefore("-")
            // Skiko/AWT pull java.desktop and more; auto-module detection under-includes for
            // Compose, so bundle the full module set — reliability over image size for a dev tool.
            includeAllModules = true
            // Windows: without a console launcher, a CLI's stdout/stderr are invisible.
            windows {
                console = true
            }
        }
    }
}
