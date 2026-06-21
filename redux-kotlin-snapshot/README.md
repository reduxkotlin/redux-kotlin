# redux-kotlin-snapshot

This module is the **library** behind `rk snapshot` — a headless renderer that
turns redux-kotlin state into PNG. It runs your real Compose Multiplatform
screens off-screen against seeded store state, so a frame is a pure function of
the state you dispatched: `f(state) -> UI`. It diffs each render against a
committed golden image and can emit a static HTML dashboard of the run — ideal
for rapid visual verification and visual regression testing, by agents and by
people.

**Unpublished.** This is a developer tool, not a Maven artifact — it is not on
Maven Central and not in `redux-kotlin-bom`. In-repo, example apps depend on it
as a test-scoped `project(":redux-kotlin-snapshot")`. To run the CLI, install the
unified `rk` binary via a package manager (`brew install reduxkotlin/tap/rk` on
macOS Apple Silicon / Linux, `scoop install rk` on Windows — bundled JRE), or
build it from the repository:

```
./gradlew :redux-kotlin-cli:installDist
# binary:
redux-kotlin-cli/build/install/rk/bin/rk
```

Then invoke it as `rk snapshot <flags>`. Real apps can also define their own
`main` calling `yourRegistry.runCli(args)` (see [Defining a
registry](#defining-a-registry) below).

### Requires Java 17+

The tool is compiled to Java 17 bytecode and the build is pinned to JDK 17 via a
Gradle toolchain (`jvmToolchain(17)` in `build.gradle.kts`, auto-provisioned),
so `installDist` is deterministic regardless of your default Java. Compose
1.11.x + Skiko load fine at 17; do not raise the toolchain to 21. The repo ships
a [`.sdkmanrc`](../.sdkmanrc) pinning Temurin 17 — run `sdk env` in the repo
root to select it.

To pick a JDK explicitly:

```
JAVA_HOME=/path/to/jdk17+ rk snapshot --help
# macOS, if registered: JAVA_HOME=$(/usr/libexec/java_home -v 17) rk snapshot --help
```

## Defining a registry

A registry is built with the `snapshotApp { }` DSL: global defaults plus named
scenes, where each scene declares its presets and a `render { }` block that maps
a `SceneArgs` (input + theme) to a `@Composable`.

```kotlin
val mySnapshots: SnapshotApp = snapshotApp {
    defaults { width = 411; height = 891; density = 2f; theme = "dark" }
    scene("board") {
        presets("seeded", "empty")
        render { args -> boardScene(presetOf(args), themeOf(args.theme)) }
    }
}
```

The scene's input is a `SnapshotInput`: either `SnapshotInput.Preset(name)` (a
named, scene-defined fixture) or `SnapshotInput.Json(element)` (arbitrary JSON
the scene's render block decodes itself — the library never deserializes app
types). Expose the CLI from your own `main`:

```kotlin
fun main(args: Array<String>) {
    mySnapshots.runCli(args)
    kotlin.system.exitProcess(0) // Skiko leaves non-daemon threads alive
}
```

## Flags

| Flag | What it does |
|---|---|
| `--list` | Print scenes + presets as JSON, then exit. |
| `--scene <name>` | Scene to render (single shot). |
| `--preset <name>` | Render the scene from a named preset. Mutually exclusive with `--state-json`. |
| `--state-json <json>` | Inline JSON state the scene decodes. Mutually exclusive with `--preset`. |
| `--theme <name>` | Override the resolved theme. |
| `--width <dp>` / `--height <dp>` | Override render dimensions in dp. |
| `--out <path>` | Write the rendered PNG to this path. |
| `--verify <golden.png>` | Compare the render against this golden; mismatch exits 1. |
| `--batch <manifest.json>` | Render every shot in a manifest (see below). |
| `--out-dir <dir>` | Batch output directory (default `.rk-snapshots`). |
| `--golden-dir <dir>` | Golden dir for a batch; its presence switches the batch into verify mode. |
| `--json` | Emit the machine-readable report on stdout. |
| `--dashboard` | Also write a static `index.html` over the batch report. |

**Exit codes:** `0` ok · `1` render or verify failure · `2` usage error.

Defaults resolve in order: per-call flag → scene default → global default.

## Typical loop

**Single shot → golden → verify.** Render once, eyeball the PNG, then keep it as
the golden and verify against it:

```
rk snapshot --scene board --preset seeded --out board.png   # render + inspect
# accept board.png as the golden (commit it)
rk snapshot --scene board --preset seeded --verify board.png  # exit 1 on drift
```

**Batch + dashboard.** A manifest is `{ "defaults": {...}, "shots": [...] }`;
each shot has an `id`, a `scene`, exactly one of `preset` / `stateJson`, and
optional `theme` / `width` / `height` / `density` / `out`:

```json
{
  "shots": [
    { "id": "board-seeded", "scene": "board",    "preset": "seeded" },
    { "id": "board-empty",  "scene": "board",    "preset": "empty"  },
    { "id": "settings",     "scene": "settings", "preset": "default" }
  ]
}
```

```
# generate the batch + a browsable dashboard
rk snapshot --batch shots.json --out-dir build/snapshots --dashboard
# once accepted, re-run with a golden dir to verify in CI
rk snapshot --batch shots.json --out-dir build/snapshots --golden-dir goldens
```

The batch writes `report.json` under `--out-dir`; `--dashboard` adds an
`index.html` over it. A batch with any failed or mismatched shot exits 1.

## In tests

For JUnit-style assertions, `SnapshotApp.assertGolden(...)` renders a scene and
compares it to a committed golden (default `src/test/resources/snapshots/<scene>-<preset>.png`),
throwing `AssertionError` on mismatch and writing the actual PNG under
`build/snapshots/` for inspection. Record/accept goldens with
`-Dsnapshot.record=true` (or `record = true`):

```kotlin
@Test fun board() = mySnapshots.assertGolden(scene = "board", preset = "seeded")
```

## See also

- Website guide: [Snapshot testing](https://www.reduxkotlin.org/advanced/snapshot-testing)
- Reference integration: the TaskFlow sample's `snapshotUi` task and its
  [`shots.json`](../examples/taskflow/composeApp/snapshots/shots.json) manifest
  (harness in `examples/taskflow/composeApp/src/jvmTest/.../snapshot/`).
- DevTools sibling for inspecting a *running* app:
  [`redux-kotlin-devtools-cli`](../redux-kotlin-devtools-cli)
