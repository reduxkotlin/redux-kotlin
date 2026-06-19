---
tier: T1
concern: snapshot
derives_from:
  - redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotApp.kt → snapshotApp, SnapshotApp
  - redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt → runCli
  - redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotTestSupport.kt → assertGolden
  - redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/GoldenStore.kt → GoldenStore
  - examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/snapshot/TaskFlowSnapshots.kt → taskFlowSnapshots
api_files: []
rules: [C]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: b3303317, date: 2026-06-16 }
---

# Snapshot / golden UI loop

> How an agent renders a redux-kotlin screen headlessly from a known state, diffs it against a
> committed golden PNG, and reads an HTML dashboard — closing a write→render→eyeball/diff→fix loop
> for visual regressions without launching a device.

## What it is

`redux-kotlin-snapshot` is an unpublished JVM dev tool (plain `kotlin("jvm")` + `application`; not on
Maven, not in the BOM, no `.api` dump). It treats the UI as a pure function of state — `f(state) → UI`
— seeds a real redux-kotlin store, renders the resulting Compose frame headlessly to a PNG, and
optionally diffs that PNG against a committed golden. A batch run also emits a static HTML dashboard.

This is the visual counterpart to the [testing](./testing.md) verify loop: reducer/selector tests
prove the *logic*; snapshots prove the *rendered frame* a given state produces. It enforces Rule C
indirectly — a scene renders the real screen from dispatched state, so a binding that reads too wide
shows up as an unexpected pixel diff.

The CLI subcommand is `rk snapshot` (from the unified `rk` binary; install via `:redux-kotlin-cli:installDist`). Module is registered as `:redux-kotlin-snapshot` in
`settings.gradle.kts`.

## Define a scene registry

A consuming app declares scenes via the `snapshotApp { }` DSL. Each scene seeds a store, dispatches
to a known state, and returns the composable to render
(`redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotApp.kt → snapshotApp, SnapshotApp`).
The canonical reference is the TaskFlow harness, which seeds *real* TaskFlow stores and renders the
real screens:
`examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/snapshot/TaskFlowSnapshots.kt → taskFlowSnapshots`.

```kotlin
val taskFlowSnapshots: SnapshotApp = snapshotApp {
    defaults { width = 411; height = 891; density = 2f; theme = "dark" }
    scene("board") {
        presets("seeded", "empty")
        render { args -> boardScene(preset(args, "seeded"), theme(args.theme)) }
    }
}
```

A scene's `render` block receives `SceneArgs` (the `SnapshotInput` — a named `Preset` or inline
`Json` — plus the resolved theme) and returns `@Composable () -> Unit`. Dimensions/theme resolve
global → scene → per-call.

## Run the CLI

A consuming app wires its registry into a `main` that calls `runCli`
(`redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/cli/Cli.kt → runCli`):

```kotlin
fun main(args: Array<String>) = taskFlowSnapshots.runCli(args)
```

### Flags

| Flag | Meaning |
|---|---|
| `--list` | Print scenes + their presets as JSON; discover what's renderable |
| `--scene <name>` | Scene to render (single shot) |
| `--preset <name>` | Named preset for the scene (exactly one of `--preset`/`--state-json`) |
| `--state-json <json>` | Inline JSON state the scene decodes itself |
| `--theme <name>` | Override theme (e.g. `light`/`dark`) |
| `--width <dp>` / `--height <dp>` | Override render dimensions |
| `--out <path>` | Write the rendered PNG to this path (generates a golden) |
| `--verify <golden.png>` | Compare the render against this golden (single shot) |
| `--batch <manifest.json>` | Render every shot in a batch manifest |
| `--out-dir <dir>` | Batch output dir (default `.rk-snapshots`) |
| `--golden-dir <dir>` | Golden dir; its presence switches the batch to verify mode |
| `--json` | Emit machine JSON on stdout |
| `--dashboard` | Also write a static `index.html` over the batch report |

### Exit codes

- `0` — ok (render/verify passed).
- `1` — render failure, or golden mismatch (single `--verify` mismatch, or any batch failed/mismatched).
- `2` — usage error (missing/ambiguous flags, bad `--state-json`, manifest not found, unknown scene).

## The golden workflow

Goldens are committed PNGs; the loop is generate → review → verify.

1. **Generate** the golden the first time (no `--verify`):

   ```
   rk snapshot --scene board --preset seeded --out goldens/board-seeded.png
   ```

2. **Review** the PNG by eye — this is the human/agent acceptance step. Commit it once correct.

3. **Verify** later renders against it:

   ```
   rk snapshot --scene board --preset seeded --verify goldens/board-seeded.png
   ```

   Prints `verify: <verdict> (<pct>%)`; exits `1` on `MISMATCH`. If the golden file is missing it
   exits `2` with a fix-it message telling you to generate it with `--out` first.

### From a jvmTest (assertGolden)

In-test, the same loop is one call —
`redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/SnapshotTestSupport.kt → assertGolden`:

```kotlin
taskFlowSnapshots.assertGolden(scene = "board", preset = "seeded")
```

It renders, compares against `goldenDir/<name>.png` via
`redux-kotlin-snapshot/src/main/kotlin/org/reduxkotlin/snapshot/GoldenStore.kt → GoldenStore`, and on
mismatch throws `AssertionError` and writes the actual PNG under `build/snapshots/` for inspection.
Run with `-Dsnapshot.record=true` to (over)write the golden instead of asserting — the standard
"review the diff, then accept" record step.

## The batch + dashboard loop (TaskFlow)

The TaskFlow harness exposes a `snapshotUi` task fed by a batch manifest at
`examples/taskflow/composeApp/snapshots/shots.json` (each shot = `{ id, scene, preset }`):

```bash
./gradlew :examples:taskflow:composeApp:snapshotUi --args="--batch examples/taskflow/composeApp/snapshots/shots.json --out-dir build/snapshots --dashboard"
```

This renders every shot, writes `report.json` + (with `--dashboard`) a static `index.html` under
`--out-dir`, and prints a `batch: N/M ok, …` summary. Pass `--golden-dir <dir>` to flip the batch into
verify mode (compares each shot against its committed golden); the run exits `1` if any shot failed or
mismatched. Open the dashboard `index.html` to eyeball golden vs. actual vs. diff per shot.

## Typical agent workflow

1. Write/extend a screen, then add or pick a scene in the registry (`snapshotApp { }`).
2. `rk snapshot --list` — confirm the scene + presets are registered.
3. `rk snapshot --scene <s> --preset <p> --out goldens/<s>-<p>.png` — render `f(state) → PNG`.
4. Eyeball the PNG; commit it as the golden when correct.
5. After later edits: `rk snapshot --scene <s> --preset <p> --verify goldens/<s>-<p>.png`
   (or the batch `snapshotUi --dashboard` over `shots.json`).
6. On mismatch, open the dashboard / `build/snapshots/<name>.actual.png`, decide intended-vs-regression,
   then fix the code or re-record the golden.

## See also

- [testing.md](./testing.md) — the logic-side verify loop (reducer/selector/store/effects tests).
- [compose-binding.md](./compose-binding.md) — Rule C render isolation, which snapshots exercise.
- [README](./README.md)
