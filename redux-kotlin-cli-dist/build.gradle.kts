plugins {
    id("convention.common")
    kotlin("jvm")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.jreleaser)
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

// Publishes the per-OS bundled-JRE archives (built by the CI matrix) as a GitHub Release plus a
// Homebrew formula and Scoop manifest. distributionType=JLINK = "archive contains a bundled JRE".
// See docs/superpowers/specs/2026-06-19-rk-publishing-research.md for the verified field values.
//
// Capture Gradle project version here: inside jreleaser{} the name `project` resolves to JReleaser's
// own DSL object, so project.version would return the JReleaser Property<String> toString() form.
val gradleVersion: String = project.version.toString()
val gradleDistDir: File = layout.buildDirectory.dir("distributions").get().asFile

jreleaser {
    gitRootSearch.set(true)
    project {
        // JReleaser wants a release-form version; CI passes JRELEASER_PROJECT_VERSION = tag.
        description.set("rk — the unified redux-kotlin CLI (devtools + snapshot)")
        copyright.set("reduxkotlin contributors")
        authors.set(listOf("reduxkotlin"))
        license.set("Apache-2.0")
        links {
            homepage.set("https://reduxkotlin.org")
        }
    }
    release {
        github {
            repoOwner.set("reduxkotlin")
            name.set("redux-kotlin")
            overwrite.set(true)
        }
    }
    distributions {
        create("rk") {
            // String-based setter (avoids import of internal model enum)
            setDistributionType("JLINK")
            artifact {
                setPath(gradleDistDir.resolve("rk-$gradleVersion-osx-aarch_64.zip").absolutePath)
                platform.set("osx-aarch_64")
            }
            artifact {
                setPath(gradleDistDir.resolve("rk-$gradleVersion-osx-x86_64.zip").absolutePath)
                platform.set("osx-x86_64")
            }
            artifact {
                setPath(gradleDistDir.resolve("rk-$gradleVersion-linux-x86_64.tar.gz").absolutePath)
                platform.set("linux-x86_64")
            }
            artifact {
                setPath(gradleDistDir.resolve("rk-$gradleVersion-windows-x86_64.zip").absolutePath)
                platform.set("windows-x86_64")
            }
        }
    }
    packagers {
        brew {
            active.set(org.jreleaser.model.Active.RELEASE)
            multiPlatform.set(true)
            repository {
                active.set(org.jreleaser.model.Active.RELEASE)
                repoOwner.set("reduxkotlin")
                name.set("homebrew-tap")
            }
        }
        scoop {
            active.set(org.jreleaser.model.Active.RELEASE)
            repository {
                active.set(org.jreleaser.model.Active.RELEASE)
                repoOwner.set("reduxkotlin")
                name.set("scoop-bucket")
            }
        }
    }
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
