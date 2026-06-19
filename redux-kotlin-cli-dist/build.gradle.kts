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

// Archives the per-OS app-image under build/distributions with the JReleaser platform suffix
// (e.g. rk-1.0.0-osx-aarch_64.zip). Each CI runner builds + archives its own host's image;
// JReleaser (Task 3) consumes these by exact path. macOS/Windows → zip; Linux → tar.gz.
// NOTE: archive filename uses the FULL version (project.version, e.g. 1.0.0-alpha01) so it matches
// JReleaser's artifact paths in Task 3. Only jpackage's packageVersion (Task 1) strips the qualifier.
tasks.register<Zip>("packageRkArchiveZip") {
    val osName = System.getProperty("os.name").lowercase()
    onlyIf { osName.contains("mac") || osName.contains("windows") }
    dependsOn("createDistributable")
    val ver = project.version.toString()
    val platform = rkJReleaserPlatform()
    archiveFileName.set("rk-$ver-$platform.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
}

tasks.register<Tar>("packageRkArchiveTar") {
    val osName = System.getProperty("os.name").lowercase()
    onlyIf { osName.contains("linux") }
    dependsOn("createDistributable")
    compression = Compression.GZIP
    val ver = project.version.toString()
    val platform = rkJReleaserPlatform()
    archiveFileName.set("rk-$ver-$platform.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
}

// Umbrella: builds whichever archive matches the current host OS.
tasks.register("packageRkArchive") {
    dependsOn("packageRkArchiveZip", "packageRkArchiveTar")
}

/**
 * Maps the build host's os.name/os.arch to JReleaser's platform string (e.g. `osx-aarch_64`).
 * Used to name the per-OS archive so JReleaser can reference it by exact path.
 */
fun rkJReleaserPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osTok = when {
        os.contains("mac") -> "osx"
        os.contains("win") -> "windows"
        else -> "linux"
    }
    val archTok = when (arch) {
        "aarch64", "arm64" -> "aarch_64"
        "x86_64", "amd64" -> "x86_64"
        else -> arch
    }
    return "$osTok-$archTok"
}
