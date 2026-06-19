# Unified `rk` CLI — Phase 2 (packaging + publishing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship `rk` as self-contained per-OS archives (bundled JRE — no Java required) published to a Homebrew tap + Scoop bucket via JReleaser. Closes #367.

**Architecture:** Route II — Compose Desktop `nativeDistributions` (`createDistributable`) builds a jpackage app-image with a bundled jlink JRE per OS. A Gradle task archives that app-image with a JReleaser-named platform suffix. A per-OS GitHub Actions matrix builds the archives; one release job runs JReleaser (`distributionType = JLINK`) to publish a GitHub Release + Homebrew formula + Scoop manifest.

**Tech Stack:** Compose Multiplatform desktop packaging (jpackage), JReleaser 1.24.0 (Gradle plugin), GitHub Actions matrix.

**Reference (verified tooling facts):** `docs/superpowers/specs/2026-06-19-rk-publishing-research.md`. **Read it before Task 3.**

## Global Constraints

- Module touched: `:redux-kotlin-cli` (Phase 1 created it; `kotlin("jvm")` + `application`, binary `rk`, `mainClass = org.reduxkotlin.cli.MainKt`). It is a repo tool — NOT Maven-published, NOT in the BOM.
- Compose app-image task: `createDistributable` → output `redux-kotlin-cli/build/compose/binaries/main/app/rk` (Linux/Windows dir) or `…/app/rk.app` (macOS bundle). Launcher: Linux `rk/bin/rk`; Windows `rk/rk.exe`; macOS `rk.app/Contents/MacOS/rk`.
- **Two version strings, by design.** The **archive filename** and JReleaser use the FULL release version (the tag, e.g. `1.0.0-alpha01`); only jpackage's `packageVersion` strips the qualifier (jpackage rejects `-alpha01`) via `project.version.toString().substringBefore("-")` (→ `1.0.0`, MAJOR>0 for macOS). CI MUST pass `-Pversion=<tag>` to every Gradle invocation (as `release.yml` already does with `-Pversion=${VERSION//v}`); otherwise `project.version` stays `1.0.0-SNAPSHOT` from `gradle.properties` and the archive names won't match JReleaser's expected paths.
- **JDK:** every runner needs a full **JDK 17 that includes `jpackage`** (Temurin 17 does). The Gradle toolchain must resolve to that JDK, not a JRE. The bundled runtime is jlink'd from it.
- **No ProGuard:** use `createDistributable` (NOT `createReleaseDistributable`) — ProGuard breaks clikt and kotlinx.serialization reflection. Larger image, but correct.
- Windows CLI needs `windows { console = true }` or stdout is invisible from a terminal.
- App-image carries host-specific Skiko → **must build on each target OS** (no cross-compile).
- JReleaser `distributionType = JLINK` (bundled JRE). Platform strings (underscore in arch): `osx-aarch_64`, `osx-x86_64`, `linux-x86_64`, `windows-x86_64`.
- Tokens: `JRELEASER_GITHUB_TOKEN` (main repo release); tap/bucket need a separate PAT in `JRELEASER_HOMEBREW_GITHUB_TOKEN` / `JRELEASER_SCOOP_GITHUB_TOKEN` (the Actions default `GITHUB_TOKEN` cannot push to them).
- Pre-commit hook runs `detektAll --auto-correct`; never `--no-verify`.
- **Testability note:** Tasks 1–2 are fully verifiable on the dev/CI host now. Task 3 is verifiable via `jreleaserConfig` (dry run, no publish). Tasks 4–6 (actual publish, brew/scoop) can only be end-to-end validated on a real tagged release AFTER Task 0's prerequisites exist — those tasks carry explicit dry-run + first-release validation steps, not fabricated "it works" claims.

---

### Task 0: Maintainer prerequisites (gating — not code)

This task is a checklist the repo owner completes; no implementer subagent. Phase 2 publish (Tasks 4–6) cannot run end-to-end until these exist. Tasks 1–3 do NOT depend on them.

- [ ] Create repo `reduxkotlin/homebrew-tap` (empty, public).
- [ ] Create a Scoop bucket repo, e.g. `reduxkotlin/scoop-bucket` (empty, public).
- [ ] Create a PAT (classic, or fine-grained with **Contents: read+write** on both repos above).
- [ ] Add it as repo secrets on `reduxkotlin/redux-kotlin`: `TAP_PAT` and `BUCKET_PAT` (may be the same PAT).
- [ ] Confirm the exact bucket repo name chosen and update `name = …` in the Task 3 `scoop.repository` block if it differs from `scoop-bucket`.

---

### Task 1: Add Compose app-image packaging to `:redux-kotlin-cli`

**Files:**
- Modify: `redux-kotlin-cli/build.gradle.kts`

**Interfaces:**
- Produces: Gradle task `createDistributable` on `:redux-kotlin-cli`, output app-image at `redux-kotlin-cli/build/compose/binaries/main/app/rk[.app]` with a bundled JRE and an `rk` launcher.

- [ ] **Step 1: Add the Compose plugins**

In `redux-kotlin-cli/build.gradle.kts`, add to the `plugins {}` block (keep `convention.common`, `kotlin("jvm")`, `application`):

```kotlin
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
```

- [ ] **Step 2: Add the `compose.desktop` app-image config**

Append to `redux-kotlin-cli/build.gradle.kts` (after the existing `dependencies { }` block):

```kotlin
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
```

- [ ] **Step 3: Build the app-image on this host**

Run: `./gradlew :redux-kotlin-cli:createDistributable`
Expected: `BUILD SUCCESSFUL`; an app-image appears under `redux-kotlin-cli/build/compose/binaries/main/app/`.

- [ ] **Step 4: Run the bundled-JRE launcher (proves the JRE is self-contained)**

On macOS:
```bash
redux-kotlin-cli/build/compose/binaries/main/app/rk.app/Contents/MacOS/rk --version
redux-kotlin-cli/build/compose/binaries/main/app/rk.app/Contents/MacOS/rk --help
```
On Linux:
```bash
redux-kotlin-cli/build/compose/binaries/main/app/rk/bin/rk --version
```
Expected: a `rk version …` line and `--help` listing `devtools` + `snapshot`. Note `packageVersion` (jpackage metadata) is independent of the runtime `--version`, which reads `project.version` from the stamped resource — so `--version` prints whatever `project.version` is (e.g. `rk version 1.0.0-SNAPSHOT` on `master`). Record the actual printed line. If the launcher fails with a missing-module/class error, `includeAllModules = true` did not cover it — report DONE_WITH_CONCERNS with the exact error.

- [ ] **Step 5: Commit**

```bash
git add redux-kotlin-cli/build.gradle.kts
git commit -m "build(cli): Compose app-image packaging (bundled JRE) for rk"
```

---

### Task 2: Gradle task to archive the app-image with a JReleaser platform name

**Files:**
- Modify: `redux-kotlin-cli/build.gradle.kts`

**Interfaces:**
- Consumes: `createDistributable` output (Task 1).
- Produces: Gradle task `packageRkArchive` writing `redux-kotlin-cli/build/distributions/rk-<version>-<platform>.{zip|tar.gz}` where `<platform>` is the JReleaser string for the build host, and `<version>` is the stripped version. These exact paths are what Task 3's JReleaser `artifact` blocks reference.

- [ ] **Step 1: Add the archive task**

Append to `redux-kotlin-cli/build.gradle.kts`:

```kotlin
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

// Maps the build host's os.name/os.arch to JReleaser's platform string (underscore in arch).
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
```

- [ ] **Step 2: Build the archive on this host**

Run: `./gradlew :redux-kotlin-cli:packageRkArchive`
Expected: `BUILD SUCCESSFUL`; on macOS-arm64 (local, no `-Pversion`, so version is `1.0.0-SNAPSHOT`) a file `redux-kotlin-cli/build/distributions/rk-1.0.0-SNAPSHOT-osx-aarch_64.zip` exists. In CI with `-Pversion=<tag>` the name uses the tag.

- [ ] **Step 3: Verify the archive contains the bundled launcher**

On macOS (adjust the version in the name to whatever Step 2 produced):
```bash
unzip -l redux-kotlin-cli/build/distributions/rk-1.0.0-SNAPSHOT-osx-aarch_64.zip | grep -E "rk.app/Contents/MacOS/rk$|runtime"
```
Expected: the entry `rk.app/Contents/MacOS/rk` and a bundled `runtime/` (the jlink JRE) are present. Record the archive's internal top-level layout in the report — Task 5 needs to know whether the mac archive's root is `rk.app` (it is) for the brew formula.

- [ ] **Step 4: Commit**

```bash
git add redux-kotlin-cli/build.gradle.kts
git commit -m "build(cli): package rk app-image into per-OS JReleaser-named archive"
```

---

### Task 3: JReleaser configuration (`distributionType = JLINK`, brew + scoop)

**Files:**
- Modify: `gradle/libs.versions.toml` (add the JReleaser plugin)
- Modify: `redux-kotlin-cli/build.gradle.kts` (apply plugin + `jreleaser { }`)

**Interfaces:**
- Consumes: the four per-OS archives named in Task 2 (built by the CI matrix in Task 4).
- Produces: JReleaser tasks on `:redux-kotlin-cli` (`jreleaserConfig`, `jreleaserFullRelease`, …) and a `jreleaser {}` config that publishes a GitHub Release + Homebrew formula (tap `reduxkotlin/homebrew-tap`) + Scoop manifest.

- [ ] **Step 1: Add the JReleaser plugin to the catalog**

In `gradle/libs.versions.toml`: under `[versions]` add `jreleaser = "1.24.0"`; under `[plugins]` add:

```toml
jreleaser = { id = "org.jreleaser", version.ref = "jreleaser" }
```

- [ ] **Step 2: Apply the plugin**

In `redux-kotlin-cli/build.gradle.kts` `plugins {}` block add:

```kotlin
    alias(libs.plugins.jreleaser)
```

- [ ] **Step 3: Add the `jreleaser {}` config**

Append to `redux-kotlin-cli/build.gradle.kts`. The `artifact` paths MUST match Task 2's output names exactly.

```kotlin
// Publishes the per-OS bundled-JRE archives (built by the CI matrix) as a GitHub Release plus a
// Homebrew formula and Scoop manifest. distributionType=JLINK = "archive contains a bundled JRE".
// See docs/superpowers/specs/2026-06-19-rk-publishing-research.md for the verified field values.
jreleaser {
    gitRootSearch.set(true)
    project {
        // JReleaser wants a release-form version; CI passes JRELEASER_PROJECT_VERSION = tag.
        description.set("rk — the unified redux-kotlin CLI (devtools + snapshot)")
        authors.set(listOf("reduxkotlin"))
        license.set("Apache-2.0")
        links { homepage.set("https://reduxkotlin.org") }
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
            distributionType.set(org.jreleaser.model.Distribution.DistributionType.JLINK)
            executable { name.set("rk") }
            // Absolute paths built from project.version (known at config time because CI passes
            // -Pversion=<tag>). Avoids a JReleaser {{template}} inside Gradle file() and avoids
            // gitRootSearch resolving relative paths against the wrong basedir.
            val distDir = layout.buildDirectory.dir("distributions").get().asFile
            val v = project.version.toString()
            artifact {
                path.set(distDir.resolve("rk-$v-osx-aarch_64.zip").absolutePath)
                platform.set("osx-aarch_64")
            }
            artifact {
                path.set(distDir.resolve("rk-$v-osx-x86_64.zip").absolutePath)
                platform.set("osx-x86_64")
            }
            artifact {
                path.set(distDir.resolve("rk-$v-linux-x86_64.tar.gz").absolutePath)
                platform.set("linux-x86_64")
            }
            artifact {
                path.set(distDir.resolve("rk-$v-windows-x86_64.zip").absolutePath)
                platform.set("windows-x86_64")
            }
        }
    }
    packagers {
        brew {
            active.set(org.jreleaser.model.Active.RELEASE)
            multiPlatform.set(true) // one formula spanning osx-aarch_64 + osx-x86_64
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
                name.set("scoop-bucket") // update if Task 0 chose a different bucket repo name
            }
        }
    }
}
```

- [ ] **Step 4: Dry-run validate the config (no publish, no network writes)**

Create placeholder archives whose names match the resolved version, then run config. Pass the SAME `-Pversion` you'll use in CI so the Gradle-interpolated artifact paths match the placeholder names, and set `JRELEASER_PROJECT_VERSION` to the same value:
```bash
V=1.0.0-alpha01
mkdir -p redux-kotlin-cli/build/distributions
for p in osx-aarch_64 osx-x86_64 linux-x86_64 windows-x86_64; do
  case "$p" in *linux*) ext=tar.gz;; *) ext=zip;; esac
  : > "redux-kotlin-cli/build/distributions/rk-$V-$p.$ext"
done
JRELEASER_PROJECT_VERSION=$V ./gradlew :redux-kotlin-cli:jreleaserConfig -Pversion=$V
```
Expected: `BUILD SUCCESSFUL` and JReleaser prints the resolved model (distribution `rk`, four artifacts, brew + scoop packagers) with no schema errors. `jreleaserConfig` does not contact GitHub. If JReleaser reports an unknown property / wrong enum, fix the DSL against the JReleaser 1.24.0 Gradle reference (cited in the research doc) and re-run. Remove the placeholder files afterward.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml redux-kotlin-cli/build.gradle.kts
git commit -m "build(cli): JReleaser JLINK config for brew + scoop publishing"
```

---

### Task 4: Per-OS CI release workflow

**Files:**
- Create: `.github/workflows/cli-release.yml`

**Interfaces:**
- Consumes: `packageRkArchive` (Task 2), `jreleaserFullRelease` (Task 3).

- [ ] **Step 1: Add the workflow**

Create `.github/workflows/cli-release.yml`:

```yaml
name: rk CLI Release

on:
  release:
    types: [created]
  workflow_dispatch:

jobs:
  build:
    name: Build rk (${{ matrix.os }})
    strategy:
      fail-fast: false
      matrix:
        os: [macos-14, macos-13, ubuntu-latest, windows-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v6
      - name: Build + archive the app-image
        # -Pversion=<tag> so the archive filename uses the release version (matches JReleaser).
        run: ./gradlew :redux-kotlin-cli:packageRkArchive -Pversion=${{ github.event.release.tag_name || github.ref_name }} --stacktrace
      - uses: actions/upload-artifact@v7
        with:
          name: rk-dist-${{ matrix.os }}
          path: redux-kotlin-cli/build/distributions/rk-*.*

  publish:
    name: Publish (JReleaser)
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17
      - uses: actions/download-artifact@v8
        with:
          pattern: rk-dist-*
          merge-multiple: true
          path: redux-kotlin-cli/build/distributions
      - uses: gradle/actions/setup-gradle@v6
      - name: JReleaser full-release
        # -Pversion so the Gradle-interpolated artifact paths match the downloaded archive names.
        run: ./gradlew :redux-kotlin-cli:jreleaserFullRelease -Pversion=${{ github.event.release.tag_name || github.ref_name }} --stacktrace
        env:
          JRELEASER_PROJECT_VERSION: ${{ github.event.release.tag_name || github.ref_name }}
          JRELEASER_GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          JRELEASER_HOMEBREW_GITHUB_TOKEN: ${{ secrets.TAP_PAT }}
          JRELEASER_SCOOP_GITHUB_TOKEN: ${{ secrets.BUCKET_PAT }}
      - uses: actions/upload-artifact@v7
        if: always()
        with:
          name: jreleaser-logs
          path: |
            redux-kotlin-cli/build/jreleaser/trace.log
            redux-kotlin-cli/build/jreleaser/output.properties
```

- [ ] **Step 2: Validate the workflow YAML**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/cli-release.yml')); print('yaml ok')"`
Expected: `yaml ok`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/cli-release.yml
git commit -m "ci(cli): per-OS matrix build + JReleaser publish for rk"
```

- [ ] **Step 4: First-release validation (requires Task 0 done; do NOT fake)**

This cannot be verified without the tap/bucket repos + PAT secrets. Record in the report that end-to-end publish is validated on the first tagged release after Task 0, by Task 5. Do not claim it passed.

---

### Task 5: macOS `.app` → Homebrew validation + fallback (first-release risk)

**Files:** none, or (fallback only) `redux-kotlin-cli/build.gradle.kts` / a brew template.

**Why this is its own task:** JReleaser's JLINK Homebrew packager expects a `bin/`-style launcher, but Compose's macOS archive root is `rk.app` with the launcher at `rk.app/Contents/MacOS/rk`. Whether `brew install` lands a working `rk` on PATH from that layout is UNCONFIRMED and only testable on a real release.

- [ ] **Step 1: After the first publish, inspect the generated formula**

Read the formula JReleaser pushed to `reduxkotlin/homebrew-tap` (`Formula/rk.rb`). Confirm it references the macOS artifacts and defines a working `bin` install/symlink for the `rk.app/Contents/MacOS/rk` binary.

- [ ] **Step 2: Install from the tap on a Mac and run it**

```bash
brew tap reduxkotlin/tap
brew install reduxkotlin/tap/rk
rk --version    # expect: rk version <tag>
rk devtools --help
```
Expected: `rk` is on PATH and runs from the bundled JRE (no system Java needed).

- [ ] **Step 3: If `rk` is not on PATH or does not run — apply the fallback**

The likely fix is one of:
- Add a JReleaser Homebrew formula template (`src/jreleaser/distributions/rk/brew/formula.rb.tpl`) whose `install` does `prefix.install Dir["*"]` then `bin.install_symlink prefix/"rk.app/Contents/MacOS/rk" => "rk"`; or
- Repackage the macOS archive in Task 2 to a `bin/`-style layout (place a thin launcher at `bin/rk` that `exec`s the inner `Contents/MacOS/rk`).

Implement whichever the inspection in Step 1 shows is needed, re-cut the release, and re-verify Step 2. Record exactly what was required — this closes the one UNCONFIRMED item in the research doc.

---

### Task 6: Docs — brew/scoop as the primary install; close #367

**Files:**
- Modify: `redux-kotlin-cli/README.md`, `README.md`, `website/docs/advanced/DevTools.md`, `website/docs/introduction/GettingStarted.md`, `docs/agent/AGENTS-external.md`

**Interfaces:** none (docs).

- [ ] **Step 1: Update the install instructions**

In each file, present the package-manager install as primary and the repo build as the fallback:

```
# macOS / Linux
brew install reduxkotlin/tap/rk
# Windows
scoop bucket add reduxkotlin https://github.com/reduxkotlin/scoop-bucket
scoop install rk
# From source (any OS, needs JDK 17+)
./gradlew :redux-kotlin-cli:installDist   # → redux-kotlin-cli/build/install/rk/bin/rk
```
State that the brew/scoop builds bundle a JRE (no Java required). Where `redux-kotlin-cli/README.md` currently says "Phase 2 will publish…", replace with the live instructions.

- [ ] **Step 2: Re-assemble agent knowledge (AGENTS-external feeds the website page)**

Run: `bash scripts/assemble-agent-knowledge.sh && bash scripts/assemble-agent-knowledge.sh --check`
Expected: `ASSEMBLED` then `ASSEMBLE CHECK OK`.

- [ ] **Step 3: Build the website**

Run: `cd website && YARN_IGNORE_ENGINES=true yarn build`
Expected: `[SUCCESS]` (pre-existing `/faq`,`/glossary`,`/api` anchor warnings are unrelated).

- [ ] **Step 4: Commit (and close #367)**

```bash
git add redux-kotlin-cli/README.md README.md website/docs docs/agent AGENTS.md
git commit -m "docs(cli): brew + scoop as primary rk install (bundled JRE); closes #367"
```

---

## Known risks & first-release decisions

- **macOS signing / Gatekeeper.** jpackage ad-hoc-signs the `.app`, so `rk` runs on Apple Silicon (arm64 requires at least an ad-hoc signature — satisfied). `brew`-installed binaries are not quarantined (brew downloads via curl, not a browser), so the brew path should run unsigned. A `.app` downloaded **directly from the GitHub Release in a browser** carries `com.apple.quarantine` → Gatekeeper warns; document the `xattr -dr com.apple.quarantine <path>` workaround. Full Developer-ID signing + notarization removes that warning but needs an Apple Developer cert (a maintainer prereq) — treat as an optional follow-up, not a Phase 2 blocker. Validate the brew path on a real Mac in Task 5.
- **Windows SmartScreen.** The unsigned `.exe` may trigger SmartScreen on direct download; `scoop install` (user-dir install) generally avoids it. Acceptable; note in docs.
- **JReleaser packager field names** (`brew.repository.owner` vs `scoop.repository.repoOwner`) differ between packagers and the research flagged the inconsistency. The Task 3 `jreleaserConfig` dry run is the gate — if it reports an unknown property, correct against the JReleaser 1.24.0 Gradle reference and re-run. Do not assume the DSL as written compiles until the dry run is green.
- **Changelog.** JReleaser defaults to a git-commit changelog and can fail if tags/history aren't shaped as it expects. If `jreleaserFullRelease` errors on changelog, set `release { github { changelog { formatted.set(org.jreleaser.model.Active.NEVER) } } }` (or `enabled.set(false)`) for the first release. First-release item.
- **`includeAllModules = true`** bundles the full JDK module set (larger image) for Skiko/AWT reliability; revisit with `suggestRuntimeModules` only if image size becomes a problem.

## Decisions (resolved 2026-06-19)

1. **Linux ARM64 — DEFERRED.** Matrix ships macOS (arm64 + x64), linux-x64, windows-x64 only. No `ubuntu-24.04-arm` / `linux-aarch_64` artifact for now; add later if requested. (Plan as written already omits it — no change needed.)
2. **Prerelease publishing — ALPHAS INCLUDED for now.** Keep `active = RELEASE` so every non-SNAPSHOT tag (incl. `-alphaNN`) pushes brew/scoop updates. **Future toggle:** to restrict to stable, change the brew/scoop packager `active` to a condition that excludes prereleases (e.g. gate the workflow `publish` job on `!github.event.release.prerelease`, or set the packagers' `active` to `RELEASE` only when the version has no `-` qualifier). Revisit before 1.0.0 stable.
3. **macOS — AD-HOC NOW, notarize later.** Ship jpackage's ad-hoc signature; document the `xattr -dr com.apple.quarantine` workaround for direct browser downloads. Developer-ID notarization is a follow-up when a cert exists; NOT a Phase 2 blocker.

## Sequencing & honesty notes

- Tasks 1–3 are implementable and verifiable now (build app-image, archive it, dry-run JReleaser) without Task 0. Land them first.
- Tasks 4–6's end-to-end publish (real GitHub Release, brew/scoop push, mac install) requires Task 0's tap/bucket repos + PAT and a tagged release. Those steps are explicitly first-release validation — an implementer must NOT report them as passing without a real release; report status accordingly.
- Task 5 is the one genuinely-uncertain integration (macOS `.app` × Homebrew). Its fallback options are concrete; which is needed is decided by inspecting the first generated formula.
