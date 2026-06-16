---
id: snapshot-testing
title: Snapshot Testing
sidebar_label: Snapshot Testing
---

# Snapshot Testing with `rk-snapshot`

`redux-kotlin-snapshot` headlessly renders your real Compose Multiplatform
screens from seeded redux-kotlin state to PNG. Because a screen is a pure
function of the state you dispatched, each render is `f(state) -> UI`: feed it a
known store state, get back a deterministic image. It then diffs that image
against a committed **golden** and can publish a static HTML dashboard of the
run.

That gives you two things at once:

- **Rapid visual verification** — render a screen from a state in milliseconds,
  off-screen, without launching the app or clicking through to reach it. Useful
  for people, and especially for AI agents that need to *see* the UI a change
  produces.
- **Visual regression testing** — accept a golden once, then fail the build
  when a later render drifts beyond tolerance.

:::note Unpublished developer tool

`redux-kotlin-snapshot` is a JVM developer tool. It is **not** published to
Maven Central and **not** in `redux-kotlin-bom`. In this repository, example
apps depend on it as a test-scoped `project(":redux-kotlin-snapshot")`, and the
`rk-snapshot` CLI is installed from source. It requires **JDK 17+**.

:::

## The registry: `snapshotApp { }`

You describe what can be rendered with the `snapshotApp { }` DSL. It holds
global defaults plus named **scenes**; each scene declares its **presets** and a
`render { }` block that maps a `SceneArgs` (the input + resolved theme) to a
`@Composable`.

```kotlin
val mySnapshots: SnapshotApp = snapshotApp {
    defaults { width = 411; height = 891; density = 2f; theme = "dark" }

    scene("board") {
        presets("seeded", "empty")
        render { args -> boardScene(presetOf(args), themeOf(args.theme)) }
    }
    scene("settings") {
        presets("default", "offline-failing", "online-bot")
        render { args -> settingsScene(presetOf(args), themeOf(args.theme)) }
    }
}
```

Inside `render { }` you build a *real* store, dispatch the actions that produce
the state you want, and return the actual screen composable — so the snapshot
exercises the same reducers and bindings the app uses, not a mock.

### `SnapshotInput`: presets vs JSON state

A scene renders from a `SnapshotInput`, which is one of:

- `SnapshotInput.Preset(name)` — a named fixture defined by the scene
  (`"seeded"`, `"empty"`, …). Presets are declared with `presets(...)` so they
  show up in `--list` and validation.
- `SnapshotInput.Json(element)` — arbitrary JSON that the scene's `render` block
  decodes itself. The library never deserializes your app types; you own the
  mapping from JSON to store state.

Defaults resolve per-call → per-scene → global, so a shot can override theme or
dimensions without touching the scene.

## The CLI: `rk-snapshot`

Install the bundled CLI from the repository:

```
./gradlew :redux-kotlin-snapshot:installDist
# binary: redux-kotlin-snapshot/build/install/rk-snapshot/bin/rk-snapshot
```

The bundled binary renders a built-in demo registry. To drive **your** scenes,
define a `main` that calls `runCli`:

```kotlin
fun main(args: Array<String>) {
    mySnapshots.runCli(args)
    kotlin.system.exitProcess(0) // Skiko leaves non-daemon threads alive
}
```

### Flags

| Flag | What it does |
|---|---|
| `--list` | Print scenes + presets as JSON, then exit. |
| `--scene <name>` | Scene to render (single shot). |
| `--preset <name>` | Render from a named preset. Mutually exclusive with `--state-json`. |
| `--state-json <json>` | Inline JSON state the scene decodes. Mutually exclusive with `--preset`. |
| `--theme <name>` | Override the resolved theme. |
| `--width <dp>` / `--height <dp>` | Override render dimensions in dp. |
| `--out <path>` | Write the rendered PNG to this path. |
| `--verify <golden.png>` | Compare the render against this golden; mismatch exits 1. |
| `--batch <manifest.json>` | Render every shot in a manifest. |
| `--out-dir <dir>` | Batch output directory (default `.rk-snapshots`). |
| `--golden-dir <dir>` | Golden dir for a batch; its presence switches the batch into verify mode. |
| `--json` | Emit the machine-readable report on stdout. |
| `--dashboard` | Also write a static `index.html` over the batch report. |

**Exit codes:** `0` ok · `1` render or verify failure · `2` usage error.

## The golden workflow

The golden loop is *generate → review → accept → verify*:

```
# 1. render once and look at the PNG
rk-snapshot --scene board --preset seeded --out board.png

# 2. accept it — commit board.png as the golden

# 3. verify later renders against it (CI / pre-merge)
rk-snapshot --scene board --preset seeded --verify board.png
```

Step 3 exits `1` on drift. To update a golden after an intended UI change,
re-run step 1 over the same path and re-commit.

In JUnit-style tests, `SnapshotApp.assertGolden(...)` does the same against a
golden under `src/test/resources/snapshots/`, writing the actual PNG to
`build/snapshots/` on mismatch. Record goldens with `-Dsnapshot.record=true`.

## Batch manifests + the dashboard

For a whole screen set, drive a JSON manifest. It is `{ "defaults": {...},
"shots": [...] }`; each shot has an `id`, a `scene`, exactly one of `preset` /
`stateJson`, and optional `theme` / `width` / `height` / `density` / `out`:

```json
{
  "shots": [
    { "id": "board-seeded",     "scene": "board",    "preset": "seeded" },
    { "id": "board-empty",      "scene": "board",    "preset": "empty" },
    { "id": "settings-default", "scene": "settings", "preset": "default" },
    { "id": "settings-offline", "scene": "settings", "preset": "offline-failing" }
  ]
}
```

```
# generate every shot + a browsable dashboard
rk-snapshot --batch shots.json --out-dir build/snapshots --dashboard

# once accepted, verify in CI by pointing at the golden dir
rk-snapshot --batch shots.json --out-dir build/snapshots --golden-dir goldens
```

The batch writes a `report.json` under `--out-dir`; `--dashboard` adds an
`index.html` that shows every shot (and, in verify mode, its diff). A batch with
any failed or mismatched shot exits `1`.

## Walkthrough: the TaskFlow sample

The [TaskFlow](../introduction/examples) sample is the reference integration.
Its scenes seed a *real* TaskFlow store and render the real `BoardScreen` /
`SettingsScreen`, so each frame is a pure function of the dispatched state — the
harder, store-bound case the tool exists for. The harness lives in
`examples/taskflow/composeApp/src/jvmTest/.../snapshot/` (kept off the
production classpath), exposes a `snapshotUi` Gradle task (group `render`), and
ships a ready-to-run manifest at
`examples/taskflow/composeApp/snapshots/shots.json`.

Run it from the repo root:

```
./gradlew :examples:taskflow:composeApp:snapshotUi --args="--batch examples/taskflow/composeApp/snapshots/shots.json --out-dir build/snapshots --dashboard"
```

Paths in `--args` resolve against the repo root (the task's `workingDir`). The
result is a set of PNGs plus `build/snapshots/index.html`. You can also render a
single screen:

```
./gradlew :examples:taskflow:composeApp:snapshotUi --args="--scene board --preset seeded --out board.png"
```

## See also

- Module README: `redux-kotlin-snapshot/README.md` in the repository.
- The sibling tool for inspecting a *running* app: the [DevTools](./devtools)
  family and the [DevTools CLI how-to](./devtools-cli-tutorial).
