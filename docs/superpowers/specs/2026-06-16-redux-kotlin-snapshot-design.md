# redux-kotlin-snapshot — design

**Status:** approved design (brainstorm), pre-plan
**Date:** 2026-06-16
**Module:** `redux-kotlin-snapshot` (new, experimental, BOM-listed)

## 1. Summary

A library that turns **`f(state) → UI`** into a first-class developer and AI-agent
primitive: render any Compose Multiplatform screen or component to a PNG
**headlessly** (JVM/Skiko, no Android emulator, no network), driven by
**synthesized Redux state**. It ships:

- a render engine over Compose's `ImageComposeScene`,
- an app-registered **scene** model (`f(input) → @Composable`),
- a **CLI** for the agent/ad-hoc loop (synthesize state → render → inspect pixels),
- a **golden-image test** front-end for regression,
- a **static HTML dashboard** generated from a canonical JSON report,
- a per-shot **semantic/text dump** so agents verify content deterministically.

It generalizes the working spike at `examples/taskflow/.../render/` (the
`renderUi` Gradle task, `RenderCli`/`RenderScenes`/`RenderStateSpec`).

## 2. Goals / non-goals

**Goals**
- Rapid UI validation: state in, rendered frame out, in ~50ms warm.
- Regression testing: golden images with perceptual diffing + a CI gate.
- Agent feedback loop: a machine-readable CLI an agent drives unattended.
- Process visibility: HTML dashboards/reports for people *and* agents, including
  evolution across iterations.
- Easy integration for any redux-kotlin Compose app (and, secondarily, any
  Compose Multiplatform app).

**Non-goals (v1)**
- Re-implementing generic Compose screenshot infrastructure. We **wrap/adopt**
  mature tooling (Roborazzi) for the diff comparator and for future Android
  fidelity; we **consume `@Preview`** as a scene source rather than rebuilding
  preview discovery.
- Android-fidelity rendering (real device fonts/resources) — deferred behind the
  backend seam; v1 is desktop-Skia only and labeled as such.
- Interaction/animation/perf testing. Snapshots are single settled frames.
- A running server. The dashboard is static HTML, a pure function of the report.

## 3. Differentiation — what we build vs. what we wrap

A multi-lens review established that ~40% of a naive design would duplicate
`ComposablePreviewScanner` + Roborazzi (which already do CMP-JVM preview
discovery, golden record/verify, and diffing). The defensible, **not-off-the-shelf**
value — uniquely enabled by the redux-kotlin substrate — is:

1. **State-seeded rendering** — `f(redux-state) → UI`: seed a real `Store` from a
   state/action spec and render the bound screen. Every other tool is
   `f(@Composable args) → UI`.
2. **Agent CLI** — a standalone `state-json → PNG` loop with a machine-readable
   capability manifest and report. Competitors are test-runner-bound.
3. **DevTools capture replay** (v2) — replay an `rk-devtools` `.jsonl` action log
   into a per-step **filmstrip**. No Kotlin precedent.

**Build:** the `ImageComposeScene` CLI engine (the agent-loop unlock a
test-runner can't give), the redux scene/seed DSL, the semantic dump, the report
schema, the HTML dashboard, the devtools-replay path.
**Wrap / adopt:** the image **diff comparator** and (later) the **Android backend**
via Roborazzi; **`@Preview`** as an additional scene source.
*(Exact Roborazzi/CMP versions and APIs are verified at implementation time.)*

## 4. Architecture & module layout

`redux-kotlin-snapshot` — a **JVM library** (`kotlin("jvm")` + Compose compiler &
multiplatform plugins; renders via `compose.desktop.currentOs`/Skiko).
Experimental; added to `redux-kotlin-bom`. Depends on `redux-kotlin` (core
`Store`) + Compose. Consumed by an app **from its own `jvm`/`jvmTest` source
set**, where `commonMain` screens are visible.

| Unit | Responsibility | Knows about |
|---|---|---|
| `RenderBackend` (internal) + `ImageComposeSceneBackend` | `render(RenderSpec) → RenderResult{png, semantics}`. Only Skiko-touching code. | Compose, Skiko |
| `RenderSpec` | `{widthDp, heightDp, density, background?, content: @Composable}` | Compose |
| `Scene` | `name` + defaults + `render: (SceneArgs) → @Composable`. App fixture. | app screens/store (via closure) |
| `SnapshotInput` (sealed) | `Preset(name)` \| `Json(JsonElement)` \| `Actions(list)` (v2). App decodes. | nothing |
| `snapshotApp { }` registry/DSL | scenes + global defaults; resolves a request | scenes |
| `SemanticsExtractor` | pull text strings + node/role tree from the rendered scene | Compose semantics |
| `BatchRunner` | manifest → render each → images + semantics + JSON report (+ verify) | registry, backend, goldens |
| `Differ` (adopts Roborazzi comparator) | golden vs actual: tolerance, diffPercent, diff PNG, changed-region boxes | image bytes |
| `GoldenStore` | read/write goldens + per-run history dirs | filesystem |
| `runCli(args)` | arg-parse (Clikt) → single / list / batch / verify; `--json`, `--scale` | registry, runner |
| `assertGolden(...)` test support | JUnit helper; record/verify | registry, backend, goldens |
| `DashboardGenerator` | `report.json (+images) → static index.html` | report schema only |

**Data flow (one pipe, multiple scopes):**
```
CLI args / test call / batch manifest
  → resolve: Scene + SnapshotInput + RenderSpec(global ⊕ scene ⊕ overrides)
  → scene.render(args)  ⇒  @Composable            ← the only app/redux-coupled step
  → RenderBackend.render(spec)  ⇒  { png bytes, semantics dump }
  → sink: --out file | golden compare (Differ) | collect into report.json
  → (optional) DashboardGenerator(report.json) ⇒ index.html
```

Redux coupling lives entirely in the app's `render { }` closures. The library is
state-shape-agnostic and never deserializes app types.

## 5. Scene DSL + input contract

```kotlin
val snapshots = snapshotApp {
    defaults { width = 411; height = 891; density = 2f; theme = "dark"; scale = 2f }

    // universal scene: any composable (component snapshot)
    scene("kanban-card") {
        presets("default", "labeled", "overdue")
        render { args ->                                   // args: SceneArgs { input, theme }
            val card = when (val i = args.input) {
                is Input.Preset -> demoCard(i.name)
                is Input.Json   -> decodeCard(i.json)      // app decodes its own DTO
                else            -> demoCard("default")
            }
            theme(args.theme) { KanbanCard(card, selected = false, onMove = {}, onOpen = {}) }
        }
    }

    // store-bound screen via redux sugar
    reduxScene("board") {
        presets("seeded", "empty")
        store { createConcurrentModelStore(notificationContext = Inline) { /* models + reducers */ } }
        seed { store, args -> /* map input -> dispatches (app code) */ }
        content { store, args -> theme(args.theme) { BoardScreen(store) } }
    }

    // @Preview as a scene source (interop; opt-in)
    previewsFrom("com.example.app")          // discovered @Preview funcs become scenes
}

fun main(args: Array<String>) = snapshots.runCli(args)
```

- **`SceneArgs` = `{ input: SnapshotInput, theme: String? }`.** Dimensions/density
  are resolved into `RenderSpec`; the composable fills the frame.
- **`SnapshotInput`** is decoded **by the app**; the library never touches app types.
- **`presets("…")`** is metadata for `--list`/validation; handling lives in `render`.
- **`reduxScene { store/seed/content }`** is sugar over `scene { render { … } }`.
  It must enforce `NotificationContext.Inline` (load-bearing for synchronous
  settle) and must not own store threading/lifecycle beyond the render.
- **`previewsFrom(...)`** consumes existing `@Preview`s as scenes (so apps don't
  double-author), reusing preview discovery rather than reinventing it.
- **Component scenes are first-class**: a scene may render any composable with
  plain params + no-op callbacks; no store required.

## 6. Render backend, RenderSpec, determinism harness

```kotlin
internal interface RenderBackend { fun render(spec: RenderSpec): RenderResult }
data class RenderSpec(val widthDp: Int, val heightDp: Int, val density: Float,
                      val background: Color? = null, val content: @Composable () -> Unit)
data class RenderResult(val png: ByteArray, val semantics: SemanticsDump)
```

- **Sizing:** DSL/CLI think in **logical dp + density**; engine renders
  `px = round(dp × density)` (so `411×891 @2x → 822×1782 px`, matching the spike).
  A separate **`--scale`** (vision legibility) multiplies output px without
  changing layout dp. Units are dp-everywhere; the spike's px-direct shortcut is
  removed to keep golden reproducibility.
- **Determinism harness (first-class, not per-scene boilerplate):** a documented
  public surface to pin clock, id generator, locale, density, **a bundled font**
  (ship a `.ttf`; force it as the snapshot FontFamily to remove OS fallback
  nondeterminism), disable animations (settle to final frame), and inject a
  **synchronous fake image loader per shot** (not a process-global singleton — the
  spike's `setSingletonImageLoaderFactory` mutation leaks across batch shots).
- **Canonical render host:** goldens are recorded **and** verified on one pinned
  Linux container (pinned JDK + Skiko/Compose version); goldens are explicitly
  **host-locked, desktop-Skia renders — not device truth.**

**Android-adapter future fit:** the Android backend implements this same
`RenderBackend` but runs under the Robolectric/Roborazzi test runner, so it lives
in a separate source set and is selected by the **test** front-end. The **CLI is
permanently JVM/Skia-only** (you can't run Robolectric from a `JavaExec`).

## 7. Semantic / text dump

Beside each PNG, the engine emits a `SemanticsDump`: the rendered **text strings**
and a **node/role tree** (extracted from the scene's semantics; Compose
`ImageComposeScene` exposes semantics owners). Rationale (top agent-loop lever):
vision models misread dense/small UI text — the dump lets an agent **assert
content deterministically** (`title == "Ship v1"`, list has 2 cards) and reserve
vision for layout/appearance. Written as `<id>.semantics.json`; referenced from
the report; surfaced in the dashboard.

## 8. Batch manifest + report schema

**Input manifest** (`--batch shots.json`): list of independent shots; per-shot one
of `preset`/`stateJson`; optional `theme/width/height/density/scale/tolerance/out`.

**Report (`report.json`)** — canonical, machine-first; drives agent + CI + dashboard.

Top-level: `schemaVersion`, `runId`, `timestamp`, `gitSha`, `gitBranch`,
`env{os,jdk,skikoVersion,dockerImage,fontsHash}`, `toleranceDefaults`,
`totals{total,match,mismatch,missingGolden,recorded,error}`, `wallTimeMs`,
`exitCode`, `prevRunId`.

Per shot:
```json
{
  "id": "board-seeded", "scene": "board", "input": { "preset": "seeded" },
  "theme": "dark", "sizeDp": [411,891], "sizePx": [822,1782], "scale": 2,
  "out": ".rk-snapshots/runs/<runId>/board-seeded.png",
  "semantics": ".rk-snapshots/runs/<runId>/board-seeded.semantics.json",
  "bytes": 59714, "renderMs": 92, "settled": true, "status": "ok",
  "warnings": [],
  "verify": {
    "golden": "golden/board-seeded.png", "result": "match",
    "diffPercent": 0.0, "marginPercent": 1.0, "diffImage": null,
    "changedRegions": [], "prevDiffPercent": 0.0
  }
}
```
- `status` ∈ `ok | error` (`error` carries `{message, stage: resolve|render|write}`).
- `verify.result` ∈ `match | mismatch | missing-golden | recorded`.
  `missing-golden`/`recorded` are **non-green** in CI summaries (warn, never silent pass).
- `changedRegions` = bounding boxes + per-region % so an agent localizes a
  regression without re-vision-ing both images.
- **History:** runs are written to `runs/<runId>/` (never overwritten);
  `runs/index.json` lists runs; `prevDiffPercent`/`prevRunId` enable
  convergence/trend views.
- **Flow (v2):** a `steps` shot type emits `id.000.png …` and a `flow{id,name,frameIndex,frameCount}` per record.

A non-zero `error`/`mismatch` (or unblessed `missing-golden`) → CLI exits non-zero.

## 9. Golden diffing

- **Perceptual + tolerance by default** (byte-exact is not viable across hosts).
  `tolerance` (per-channel) + `maxDiffPercent` gate, per-shot overridable; always
  report `diffPercent` and `marginPercent` (surfaces silent erosion near the
  threshold).
- **Adopt Roborazzi's comparator** rather than hand-rolling, where it fits the
  JVM/Skia path; emit a **diff PNG** + **changed-region boxes**.
- **Determinism is enforced upstream** (§6): pinned host + bundled font + fakes;
  diffing tolerance papers over residual AA only, not systemic drift.
- **Golden bootstrap:** new goldens land as `recorded`/`missing-golden`
  (needs-blessing), never an automatic green — prevents enshrining a wrong baseline.

## 10. CLI front-end

```
rk-snapshot --scene board --preset seeded --theme dark --out b.png
rk-snapshot --scene kanban-card --state-json '{...}'   --scale 2 --out c.png
rk-snapshot --list                       # JSON: scenes, presets, defaults, stateExamples, latency note
rk-snapshot --batch shots.json --out-dir .rk-snapshots [--verify --golden-dir golden] [--update-goldens]
rk-snapshot --scene board --preset seeded --verify golden/board-seeded.png
rk-snapshot ... --json                   # machine JSON on stdout only; human logs to stderr
rk-snapshot ... --dashboard              # also emit index.html over the run's report.json
```
- Input is exactly one of `--preset`/`--state-json`/`--state-file` (validated).
- **Structured errors**: bad input → JSON `{error, field, expected, got}` (with
  `--json`), not prose; **batch isolates per-shot failures** (one bad shot ≠ dead run).
- **Exit codes:** `0` ok · `1` render error / golden mismatch · `2` usage error.
- `exitProcess` stays strictly CLI-side; never reachable from `assertGolden`.

## 11. Test front-end

```kotlin
class ScreenSnapshots {
    private val snaps = AppSnapshots.registry
    @Test fun board_seeded()  = snaps.assertGolden("board", preset = "seeded")
    @Test fun card_overdue()  = snaps.assertGolden("kanban-card", preset = "overdue", theme = "light")
}
```
- Renders via the registry + a `RenderBackend`; compares to a committed golden
  (`src/jvmTest/resources/snapshots/<name>.png`, overridable); on mismatch fails
  the test and writes `actual` + `diff` to `build/snapshots/`.
- Record/update via `-Dsnapshot.record=true` / `--update-goldens`.
- This is the **backend selection point** for the future Android (Roborazzi)
  adapter; the CLI stays JVM/Skia.

## 12. HTML dashboard (v1)

A **static `index.html`**, a pure function of `report.json` (+ image files);
self-contained (inline CSS/JS, relative image refs); no server.

- **Summary bar:** runId, timestamp, gitSha, env (jdk/skiko/docker/fontsHash),
  totals, wall-time, exit status (big red/green).
- **Gallery:** card per shot (actual thumbnail, id, scene, status pill,
  diffPercent, renderMs); mismatches float to top.
- **Detail triptych:** `golden | actual | diff` side-by-side (the PR-review
  surface — binary PNG diffs are otherwise unreviewable), plus the **state/input**
  that produced the frame and the **semantic dump**.
- **History (cheap):** read current + `prevRunId` → show per-shot trend
  (`prevDiffPercent`), enabling "is this UI converging or thrashing" for agents
  and humans; integrates with the external PM-viewer.
- **v2:** filmstrips for flows, onion-skin slider, in-page accept/reject export,
  SSIM mode, PR-bot image comments.

## 13. Error handling — "a snapshot run never crashes"

- **Per-shot isolation:** a throwing scene → `status:"error"` + stage; batch continues.
- **Engine path uses catch-per-shot, not a settle-timeout** —
  `ImageComposeScene.render()` is one synchronous frame, so the unregistered-model
  footgun *throws* (it doesn't hang). Eagerly validate model registration where
  possible. A settle-timeout applies only to an idle-waiting **test** path
  (`waitForIdle`), not the CLI engine.
- **Validation** (unknown scene/preset, both inputs) → usage error, exit 2, lists valid options.
- **Missing golden** → `missing-golden` (not a crash); fails the gate unless blessing.

## 14. Testing strategy

- **Built-in demo scene** (trivial composable, no redux) so CI exercises the full
  pipe with no app dependency: a **render smoke test** (PNG non-empty + decodes to
  expected px; proves headless `ImageComposeScene` works in CI), a **golden test**
  (committed tiny golden → exercises differ: match/dims/N-pixel cases), a
  **determinism test** (render twice → within-tolerance identical), and a
  **semantic-dump test**.
- **External-consumer spike (gate before calling it a library):** a separate
  consumer module that applies the Compose plugins, depends on the lib from
  `jvmTest`, and diffs **real goldens on the canonical Linux host** — because the
  current in-module spike does not exercise the external-dependency classpath or
  any diffing.
- **Unit:** px math, defaults-merge order, manifest round-trip, CLI arg-parse →
  resolved request, differ verdicts, error isolation, report-schema serialization,
  dashboard generation (`f(report.json)` → expected HTML).
- **CI placement:** smoke + golden tests run in a task CI actually executes; pick
  the module's **test JVM toolchain to match the highest bytecode of compose/skiko
  deps** (do **not** blind-copy `jvmToolchain(17)` — that caused the recent
  devtools-cli `UnsupportedClassVersionError`); run goldens on the pinned host.

## 15. Packaging / build / consumer requirements

- New module `redux-kotlin-snapshot`, experimental, in the BOM.
- **Consumer requirement (must document + ship a sample build):** the consuming
  app module applies the Compose compiler + multiplatform plugins and adds the lib
  to `jvmTest` (a plain `kotlin("jvm")` consumer cannot compile the app's
  `@Composable` test lambdas). Provide a documented `renderUi` `JavaExec` task
  snippet (lifted from the spike) and a sample consumer `build.gradle.kts`.
- TaskFlow becomes the **reference integration** and the repo's screenshot
  generator (capturing the in-repo-validation value).
- A Gradle plugin that auto-wires the CLI task + test source set is a deliberate
  **fast-follow** once the API stabilizes (kept out of v1 so we don't freeze
  conventions early).

## 16. Phasing

**v1:** engine (`ImageComposeScene`) + scene/reduxScene DSL + `@Preview` source +
semantic dump + CLI (single/list/batch/verify, `--json`/`--scale`) + perceptual
golden diff (adopt Roborazzi comparator) + JUnit `assertGolden` + **static HTML
dashboard** + per-run history + determinism harness + TaskFlow as first consumer +
external-consumer CI gate.

**v2:** action-replay (incl. rk-devtools `.jsonl`) → multi-step **flow/filmstrip**;
Android-fidelity backend (Roborazzi adapter); dashboard onion-skin/trend charts;
optional Gradle plugin.

## 17. Risks / open questions

- **Determinism is the central risk.** Mitigated by pinned host + bundled font +
  fakes + perceptual tolerance; must be proven by the external-consumer spike
  before GA.
- **Vision ceiling** on dense UI — mitigated by the semantic dump; document the loop's limits.
- **Synthesized ≠ real state** — `f(state)` validates the view given state, not
  that reducers produce it; pair with reducer unit tests, and v2 action-replay
  narrows the gap.
- **Roborazzi/CMP version + API surface** for the comparator and `@Preview`
  discovery — verify exact versions and integration seams at implementation time.
- **Golden repo bloat** — minimize shot count, optimize PNGs deterministically,
  keep diff/actual as CI artifacts only (never committed); consider git-lfs for goldens.
