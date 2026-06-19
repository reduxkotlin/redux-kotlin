# Unified `rk` CLI — design

Date: 2026-06-19
Status: approved (brainstorm) — pending spec review → implementation plan

## Problem

redux-kotlin ships two terminal tools, both unpublished and obtainable only by
cloning the monorepo and running `installDist`:

- `rk-devtools` (`redux-kotlin-devtools-cli`) — generic live/headless app
  inspector (action log, JSON diffs, per-store `.jsonl` captures) over the
  devtools bridge.
- `rk-snapshot` (`redux-kotlin-snapshot`) — renders Compose screens from state
  to PNG with golden diffing.

Two problems:

1. **Discoverability** — agents/users in their own app project cannot obtain
   either tool (no Maven coordinate, no package manager, no release binary). The
   file users drop in their repo did not even mention the CLI. *(Partially fixed
   already: an `AGENTS-external.md` CLI section landed in PR #387.)*
2. **Fragmentation** — two separate binaries to learn and install. Developers
   should install **one** redux-kotlin CLI.

## Goals

- A single CLI binary, `rk`, with grouped subcommands: `rk devtools …` and
  `rk snapshot …`.
- Easy install for end users: `brew install reduxkotlin/tap/rk` (macOS/Linux),
  `scoop install rk` (Windows), with **no JDK required** (bundled runtime).
- Keep the snapshot **library** API intact for in-project use.

## Non-goals

- Publishing the CLI to Maven Central (Maven is for libraries, not CLIs).
- A lean Compose-free "agent-only" binary — not achievable while `rk snapshot`
  (Compose/Skiko) lives in the same binary. Noted as possible future work.
- Rendering arbitrary user-authored Compose scenes from the prebuilt binary
  (requires the user's compiled code — stays a library use; see below).

## Phase 1 — Unify into `rk`

### Command tree

```
rk
├── devtools   (was rk-devtools)
│   ├── serve [--port --host --token --out --ui]
│   ├── stores
│   ├── actions [--store --type --since --until --last --format --pretty]
│   ├── diff
│   ├── state --at <id>
│   └── tail [--follow]
└── snapshot   (was rk-snapshot)
    └── render / batch / diff  (built-in + manifest scenes)

rk --version    # reports project.version
rk --help       # lists the two groups
```

`diff` exists under both groups — no collision because it is namespaced.

### Module layout

- `:redux-kotlin-devtools-cli` → **library** (drop the `application` plugin).
  Rename its `RootCommand(name = "rk-devtools")` to a group
  `CliktCommand(name = "devtools")`; expose `public fun devToolsCommand(): CliktCommand`.
  Subcommands unchanged.
- `:redux-kotlin-snapshot` → **library** (drop `application`). It already exposes
  `snapshotCommand(app: SnapshotApp): CliktCommand`; rename the command to
  `name = "snapshot"`. The unified app passes the built-in `demoSnapshots`
  registry (plus the existing manifest/batch mode).
- **New `:redux-kotlin-cli`** → the only `application` module.
  `applicationName = "rk"`, `mainClass = org.reduxkotlin.cli.MainKt`. Root `rk`
  clikt command wires `.subcommands(devToolsCommand(), snapshotCommand(demoSnapshots))`.
  Depends on the two libraries.
- Old `rk-devtools` / `rk-snapshot` binaries are **retired** (unpublished; no
  external consumers): drop the `application` plugin and delete the standalone
  `MainKt`/`main` entrypoints from both modules. `rk` is the only entrypoint.
  Library symbols the aggregator needs stay public — `devToolsCommand()`,
  `snapshotCommand(app)`, and the `demoSnapshots` registry.
- Both libraries already pull `compose.desktop.currentOs`, so the combined dist
  carries Compose once (per OS), not twice.

### Snapshot constraint (important)

`rk snapshot` in the **installed binary** renders only the **built-in demo
registry and manifest-driven** scenes, because Compose rasterization needs
compiled scene code. Rendering *your own app's* screens remains a **library**
use: depend on `redux-kotlin-snapshot` and call
`snapshotCommand(yourRegistry).main(args)` from your project's tooling. Docs must
state this explicitly so users do not expect `rk snapshot render` to find their
screens.

### Version

`rk --version` reports `project.version` (the release tag, e.g. `1.0.0-alpha01`).
Inject `project.version` into `:redux-kotlin-cli` (generated `BuildConfig`-style
constant or manifest attribute) and wire a clikt `version` option. The tools have
no `--version` today.

### Process exit

Skiko/Compose leave non-daemon threads alive; the snapshot `main` already calls
`exitProcess(0)` after success. The unified `rk` `main` must do the same so the
process terminates cleanly after a command completes.

### Docs to update (rk-devtools/rk-snapshot → rk devtools/rk snapshot)

`README.md`, `website/docs/advanced/DevTools.md`, `redux-kotlin-snapshot/README.md`,
`redux-kotlin-devtools-cli/README.md`, `docs/agent/references/devtools.md`,
`docs/agent/api-map.md`, `docs/agent/AGENTS-external.md`. New build/obtain path:
`./gradlew :redux-kotlin-cli:installDist` → `redux-kotlin-cli/build/install/rk/bin/rk`.
After Phase 2, document `brew`/`scoop` install as the primary path.

### Tests

- Keep existing devtools and snapshot command tests (now nested under groups).
- Add an `rk` smoke test: `rk --help` lists `devtools` and `snapshot`;
  `rk devtools --help` and `rk snapshot --help` resolve; `rk --version` prints the
  project version.

## Phase 2 — Publishing (per-OS, bundled JRE, no JDK required)

### Why per-OS is mandatory

`compose.desktop.currentOs` resolves to a **host-specific** Skiko native
(`skiko-awt-runtime-<os>-<arch>`, e.g. `…-macos-arm64`). A dist built on one
host runs only on that OS/arch. A single universal artifact is impossible; the
release must build per platform.

### Packaging — bundled JRE via Compose jpackage

Use Compose Multiplatform's `nativeDistributions` (jpackage app-image) on
`:redux-kotlin-cli` to produce a **self-contained per-OS `rk` app-image with a
trimmed bundled JRE** (must include `java.desktop` for Skiko/AWT). This mirrors
the existing `redux-kotlin-devtools-standalone` packaging and closes #367 (no
installed Java needed). Prefer app-image archives (tar/zip) over OS installers,
since Homebrew/Scoop consume archives.

### CI matrix

Build `rk` app-images on a matrix: `macos-arm64`, `macos-x64`, `linux-x64`,
`windows-x64` (optionally `linux-arm64`). Each runner produces its platform's
archive.

### Distribution — JReleaser

Add the JReleaser Gradle plugin to `:redux-kotlin-cli`. From the per-OS
archives, JReleaser publishes:

- **GitHub Release** with all platform archives + checksums.
- **Homebrew** formula in tap repo `reduxkotlin/homebrew-tap` with per-platform
  URLs (`on_macos`/`on_arm`/`on_intel`/`on_linux`). No `depends_on openjdk`
  (JRE is bundled).
- **Scoop** manifest in a `reduxkotlin/scoop-bucket` repo for Windows.

Triggered from `release.yml` on tag, after the existing Maven Central release.

### Prerequisites (flag to maintainer)

- Create `reduxkotlin/homebrew-tap` and `reduxkotlin/scoop-bucket` repos.
- Add a release PAT secret (write access to those repos) for JReleaser.

### End-user result

```
brew install reduxkotlin/tap/rk      # macOS / Linux
scoop install rk                     # Windows
rk devtools serve
rk snapshot render --state s.json
```

No JDK required.

## OS / runtime requirements (summary)

- **JVM (build/library use):** JDK 17+, any vendor; runs on 21/24 (JVM is
  backward-compatible). Bytecode target 17.
- **Native deps:** Skiko (Compose) requires per-OS/arch natives and the
  `java.desktop`/AWT module. Headless Linux works via offscreen render but needs
  the AWT libs present.
- **Installed `rk` (Phase 2):** no JDK required — the JRE is bundled per OS.

## Risks

- **jlink vs jpackage with automatic modules.** Compose/Skiko are non-modular;
  jlink is brittle here. Use Compose's jpackage app-image path (proven by
  `-standalone`) rather than a hand-rolled jlink assembler.
- **App-image size.** Each per-OS archive bundles Compose + a JRE (~tens of MB).
  Acceptable for a dev tool; note it.
- **Library depending on an application module.** `:redux-kotlin-devtools-cli`
  depends on `:redux-kotlin-devtools-standalone` (for `--ui`); converting the
  former to a library while it depends on an `application` module is fine on the
  JVM classpath but should be verified.
- **Tap/bucket onboarding** is maintainer-side (repos + PAT); Phase 2 is blocked
  on it but Phase 1 is not.

## Sequencing

Phase 1 (unify `rk`, retire old binaries, update docs, tests) lands and goes
green first. Phase 2 (packaging + JReleaser + tap/bucket) follows as a separate
change once Phase 1 is merged.
