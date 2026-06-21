# `rk` CLI publishing — grounded tooling research (Phase 2 input)

Date: 2026-06-19. Reference for the Phase 2 packaging/publishing plan. All facts
cited from official docs (JReleaser jreleaser.org, JetBrains Compose desktop
packaging). Flagged UNCONFIRMED items need validation on a real release.

## The architecture fork (decide before planning)

Phase 2 needs per-OS, bundled-JRE archives (no Java on the user's machine) fed to
a Homebrew tap + Scoop bucket. Two coherent routes — they differ in who runs
jlink/jpackage:

### Route I — JReleaser's own `jlink` assembler (no Compose packaging)
- JReleaser runs jlink, producing a uniform `bin/rk` launcher layout on every OS.
- This is JReleaser's **proven** JLINK path (its `helloworld-java-jlink` example).
- Distribution type `JLINK`; brew/scoop are first-class.
- **Risk:** jlink over Compose/Skiko, which are non-modular *automatic* modules,
  needs `--add-modules ALL-MODULE-PATH` + jdeps and can be brittle. Skiko native
  libs must be carried as resources.

### Route II — Compose `nativeDistributions` (jpackage app-image)
- `createDistributable` → `build/compose/binaries/main/app/<Name>` with a bundled
  jlink-minimized JRE. **Reliably handles Compose/Skiko** (proven by
  `redux-kotlin-devtools-standalone`).
- **Risk:** on macOS the app-image is a `.app` bundle; the launcher is at
  `<Name>.app/Contents/MacOS/<Name>`, NOT `bin/rk`. JReleaser's JLINK brew
  packager expects a `bin/`-style image, so the macOS `.app` layout needs custom
  handling (symlink the inner binary, or a hand-tuned formula). This interaction
  is **untested** and can only be validated against a real release.

**Tension:** the spec assumed Route II ("Compose's jpackage path, proven by
-standalone"), but JReleaser's grain is Route I. Route II is safest for *building*
a working Compose binary; Route I is safest for *JReleaser/brew integration*.
This is the decision to make before writing the plan.

## Verified facts

### Compose Desktop app-image (Route II)
- Tasks: `createDistributable` (default) / `createReleaseDistributable` (ProGuard).
  Output: `build/compose/binaries/main/app/<Name>` (or `main-release/`).
- Launcher: macOS `<Name>.app/Contents/MacOS/<Name>`; Linux `<Name>/bin/<Name>`;
  Windows `<Name>/<Name>.exe` (+ `<Name>Console.exe` when `console = true`).
- Requires plugins `org.jetbrains.compose` + `org.jetbrains.kotlin.plugin.compose`
  on `:redux-kotlin-cli` (it currently has only `application` + `kotlin("jvm")`).
- `packageVersion` must be `MAJOR.MINOR.PATCH`, **no `-SNAPSHOT`**, MAJOR > 0 on
  macOS. Strip the qualifier at build time (the -standalone build already does
  `project.version.substringBefore("-")`).
- **Windows CLI:** must set `windows { console = true }` or stdout/stderr are
  invisible from a terminal.
- App-image carries host-specific Skiko → must build on each target OS (no
  cross-compile). Confirms the per-OS matrix.

### JReleaser
- Plugin `org.jreleaser` (1.24.0). Tasks: `jreleaserAssemble`,
  `jreleaserFullRelease`, `jreleaserConfig` (dry run).
- Distribution type **`JLINK`** for a bundled-JRE archive (NOT `BINARY` =
  GraalVM, NOT `JAVA_BINARY` = needs system Java). `platform` is mandatory per
  artifact.
- Platform strings (underscore in arch): `osx-aarch_64`, `osx-x86_64`,
  `linux-x86_64`, `windows-x86_64` (`linux-aarch_64`, `linux_musl-x86_64` exist).
- Homebrew: set `multiPlatform = true` for one formula spanning both macOS arches.
  Tap repo via `packagers.brew.repository { owner; name }`. Scoop via
  `packagers.scoop.repository { repoOwner; name }`.
- Tokens: `JRELEASER_GITHUB_TOKEN` (main repo, release). The Actions default
  `GITHUB_TOKEN` **cannot push to the tap/bucket repos** — needs a separate PAT
  (Contents: read+write) in `JRELEASER_HOMEBREW_GITHUB_TOKEN` /
  `JRELEASER_SCOOP_GITHUB_TOKEN`.
- CI: per-OS matrix builds + `upload-artifact` → ONE `ubuntu-latest` job
  `download-artifact (merge-multiple: true)` → `jreleaser/release-action@v2`
  `full-release`. Only the release job runs JReleaser.

### Prerequisites (maintainer, before any of this runs)
- Create `reduxkotlin/homebrew-tap` and a Scoop bucket repo (e.g.
  `reduxkotlin/scoop-bucket`).
- Create a PAT with Contents:read+write on both, store as repo secrets
  (e.g. `TAP_PAT`, `BUCKET_PAT`).

### UNCONFIRMED (validate on first real release)
- Whether JReleaser JLINK + Homebrew consumes a macOS `.app` archive cleanly, or
  needs a `bin/`-style image (Route I) / custom formula tweak.
- Whether Compose rejects a `0.x`/qualifier version at the app-image task itself
  vs only for installer formats (use `1.0.0`-form to be safe).
