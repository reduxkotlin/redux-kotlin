# redux-kotlin-snapshot: semantics dump, semantics-golden, publish — design

**Date:** 2026-07-01
**Module:** `redux-kotlin-snapshot` (+ its `rk snapshot` / `runCli` surface)
**Status:** design, pending approval

## Goal

Complete the capabilities that make `redux-kotlin-snapshot` valuable to an **AI-agent
dev loop**. The module already renders `f(state) → Composable → PNG (+ pixel golden diff)`
headlessly via Compose `ImageComposeScene` (Skiko, JVM-only). Pixels work; the
agent-facing capabilities are stubbed or missing.

### Driving use case

An external KMP app (Compose Multiplatform, `commonMain` UI, redux-kotlin store)
registers scenes via `snapshotApp {}` and runs its own CLI via `SnapshotApp.runCli(argv)`.
A coding agent verifies UI changes cheaply. Today it must `Read` PNGs (~1–2k vision
tokens each, every iteration). Two capabilities eliminate most of that cost:

1. A **text semantics dump** of the rendered scene — read content as text, not pixels.
2. **Golden-diff-as-gate** — a text verdict per shot so the agent reads a PNG only when a
   shot actually drifted.

## Scope

One spec covering all four gaps plus a forward-compat seam:

- **A.** Real semantics extraction (the P0 gap).
- **B.** Canonical semantics form + structural diff.
- **C.** CLI surface for semantics (single + batch), exit-code contract preserved.
- **D.** Batch report exposes dump + semantics verdict.
- **E.** Maven Central publish + version reconciliation.
- **F.** Live-view seam (per-shot emission + `events.ndjson`) — enables a *future* live
  web view without reshaping the batch loop later. The web server / live HTML itself is
  **out of scope** (future feature).

## Verified technical facts (Compose MP 1.11.1)

Confirmed against resolved artifacts, not training data:

- **Versions:** Kotlin 2.3.20, compose-multiplatform 1.11.1, Skiko 0.144.6, JDK
  toolchain 17. `androidx.compose.ui` = JetBrains 1.11.1.
- **Semantics access path is public + experimental, no reflection, no internal `$ui`
  access, no new `compose-ui-test` dependency:**
  - `ImageComposeScene.semanticsOwners: Collection<SemanticsOwner>` — `@ExperimentalComposeUiApi`,
    populated during composition, readable after `scene.render()` before `scene.close()`.
  - `SemanticsOwner.rootSemanticsNode: SemanticsNode` (merged) and `unmergedRootSemanticsNode`.
  - `SemanticsNode.config: SemanticsConfiguration` (public, merged), `.children: List<SemanticsNode>`
    (public), `.boundsInRoot: Rect` / `.boundsInWindow` (public), `.id`, `.isRoot`.
  - `SemanticsConfiguration` is iterable and exposes `get`/`contains` by key.
  - `SemanticsProperties.Text` (`List<AnnotatedString>`), `.ContentDescription`
    (`List<String>`), `.Role` (`Role`), `.TestTag` (`String`), plus `Disabled`, `Selected`,
    `ToggleableState`.
  - `ImageComposeScene` is **not** deprecated (only its old `render(Duration)` overload is);
    no need to migrate to `androidx.compose.ui.scene.ComposeScene`.
- **Publishing is already wired to Maven Central.** `convention.publishing-jvm` applies
  `com.vanniktech.maven.publish` with `publishToMavenCentral(automaticRelease = true)` and
  `signAllPublications()` (CI-only, gated on `signingInMemoryKey`). Coordinates
  `org.reduxkotlin:redux-kotlin-snapshot:<version>`; `version`/`group` inherited from root
  (`1.0.0-SNAPSHOT`, `org.reduxkotlin`). Release tags set `-Pversion=<tag>` (CLI is at
  `1.0.0-alpha02`).

## A. Semantics extraction

New file `Semantics.kt`. The `SemanticsDump` scaffold in `RenderBackend.kt` is expanded.

### Data model

`SemanticsDump.Node` (nested to avoid clashing with Compose's `androidx.compose.ui.semantics.SemanticsNode`):

```
@Serializable
class Node(
    val role: String?,                 // Role enum name, lowercased; null if absent
    val text: List<String>,            // AnnotatedString.text on this node, in order
    val contentDescription: List<String>,
    val testTag: String?,
    val enabled: Boolean?,             // false when SemanticsProperties.Disabled present; null if unspecified
    val selected: Boolean?,            // SemanticsProperties.Selected; null if absent
    val toggle: String?,               // ToggleableState name (On/Off/Indeterminate); null if absent
    val bounds: List<Int>?,            // [left, top, right, bottom] rounded; see determinism
    val children: List<Node>,
)

class SemanticsDump(
    val root: Node?,                   // null when nothing captured
    val texts: List<String>,           // flat, traversal order — kept for back-compat
) {
    fun toJson(): String
    fun toText(): String               // compact indented tree
    companion object { val EMPTY = SemanticsDump(root = null, texts = emptyList()) }
}
```

`RenderResult(png, semantics)` is unchanged in shape; `semantics` is now populated.

### Extraction

In `ImageComposeSceneBackend.render`, after `scene.render()` and before `scene.close()`:

1. Read `scene.semanticsOwners`; sort deterministically (by root `boundsInWindow` top, then
   left) so popups/dialogs order stably.
2. For each owner, take `rootSemanticsNode` (**merged** tree — matches the compose-ui-test
   default, carries `Role`; unmerged is deferred, YAGNI).
3. Recurse `node.children` (public, layout order — deterministic), reading
   `node.config[SemanticsProperties.*]` for each field. `bounds` from `boundsInRoot`, rounded
   to ints.
4. Build the flat `texts` list from a pre-order walk (back-compat with the current field).

Requires only `@OptIn(ExperimentalComposeUiApi::class)`.

### Determinism

- Ordering is deterministic by construction (stable owner sort + layout child order).
- **Bounds vary across arch/rounding** (dev arm64 vs CI x64). The `Node.bounds` field is
  populated in memory but is **excluded from the canonical form** (§B) and from any
  semantics-golden, so goldens are arch-independent. Bounds are serialized to JSON output
  only when explicitly requested (`--bounds`), for debugging.

## B. Canonical form + structural diff

New file `SemanticsDiffer.kt`.

- **Canonical form:** a deterministic serialization of the `Node` tree with **`bounds`
  omitted** and a fixed field order (kotlinx JSON over the data classes; field order is
  stable). This string is what a semantics golden stores.
- **Diff:** parse golden + actual canonical trees, walk by node path, emit a readable
  line-level delta:
  - `+ <path> <node summary>` — node added
  - `- <path> <node summary>` — node removed
  - `~ <path> <field>: <old> → <new>` — field changed
- Verdict: `match` when the canonical strings are equal; otherwise `mismatch` with the
  delta lines. `missing-golden` when the golden file is absent (mirrors pixel verify).

## C. CLI surface (`cli/Cli.kt`)

Existing flags, `--list` shape, batch manifest, and exit codes (0 ok / 1 render-or-verify
failure / 2 usage) are unchanged. `runCli` → `exitProcess(0)` contract preserved.

### Single shot (`runSingle`)

- `--semantics[=json|text]` (optional value, default `json`): after render, print the dump
  to stdout; or, when `--out` is given, write a sidecar `<out>.semantics.json` /
  `<out>.semantics.txt`.
- `--verify-semantics <file>`: compare the rendered canonical form against the golden file;
  print verdict + delta; exit 1 on mismatch. Independent of the pixel `--verify`.
- `--bounds`: include `bounds` in emitted semantics (debug only; never affects goldens).

### Batch (`runBatch`)

- `--semantics`: embed each shot's canonical dump (no bounds) in `report.json`. Off by
  default → `report.json` stays small.
- `--verify-semantics`: enable the semantics-golden gate. Goldens live as sidecars in the
  existing `--golden-dir` as `<id>.semantics.json`, beside `<id>.png`. A semantics mismatch
  counts as a failure (exit 1), with the delta recorded in the report. A missing semantics
  golden is `missing-golden` (non-failing, like pixels).
- `--events`: write `events.ndjson` (§F).

## D. Batch report (`Report.kt`)

`schemaVersion` bumped 1 → 2. All new fields are nullable/defaulted (backward compatible):

- `ShotReport.semantics: SemanticsDump.Node?` — present only with `--semantics`.
- `ShotReport.verifySemantics: SemanticsVerifyReport?` — present only with `--verify-semantics`.
- `SemanticsVerifyReport(golden: String, result: String /* match|mismatch|missing-golden */, delta: List<String>? )`.
- `Totals` gains `semanticsMismatched: Int` and `semanticsMissingGolden: Int`.

Batch gate becomes `failed > 0 || mismatched > 0 || semanticsMismatched > 0`.

## E. Publishing / version reconcile

Infra already targets Central; the work is cutting a **pinnable released version** and
reconciling the skew.

- **Single version line.** The snapshot module rides the same release-tag flow as the CLI.
  The next release cuts `org.reduxkotlin:redux-kotlin-snapshot:1.0.0-alpha03` to Maven
  Central (from the `-Pversion=1.0.0-alpha03` tag build). `1.0.0-SNAPSHOT` remains the
  `master` development baseline.
- **Consumer docs.** Add a module README section: the pinned coordinate, the `repositories {}`
  (Maven Central) and `dependencies {}` snippet, and a note that scene authoring also needs
  the transitive Compose/clikt/serialization `api` deps.
- **Deliverable boundary.** The actual signed publish runs in CI on a maintainer tag push
  (requires signing secrets, irreversible). This work delivers: (1) confirmation the module
  is in the release publish set, (2) version reconciliation + docs, (3) local
  `./gradlew :redux-kotlin-snapshot:publishToMavenLocal` verification that the artifact +
  POM + sources/javadoc jars resolve. The tag push itself is a follow-up maintainer action,
  noted in the plan.

## F. Live-view seam (forward-compat only)

Enables a *future* live web view (a page that updates as the agent renders shots) without
having to reshape the batch loop later. The server, SSE/auto-refresh, and live HTML are
**out of scope** here.

- `BatchRunner.run` gains an optional `onShot: (ShotReport) -> Unit` callback (default
  no-op), invoked as each shot completes (the loop already produces `ShotReport`s
  one-by-one; this exposes them incrementally instead of only in the final aggregate).
- With `--events`, the CLI writes `events.ndjson` in `outDir` — one serialized `ShotReport`
  per line, appended as each shot finishes. PNGs already write per-shot, so a future watcher
  has both artifacts and a tailable event stream.
- The existing `DashboardGenerator` (static `index.html` over the final report) remains the
  render target; a future `--serve`/`--watch` tails `events.ndjson` into it. Not built now.

## Non-goals

- Keep the render backend JVM/Skiko headless. No emulator/Robolectric.
- Headless render has no async image loading — out of scope.
- No web server / live HTML / SSE in this work (only the emission seam).
- No unmerged-semantics tree (merged only); revisit if a consumer needs it.

## Testing

- **Extraction correctness:** a scene with `Text`, a `Button` (Role), a `testTag`, a
  disabled node, and a selected node → assert the `Node` tree fields and traversal order.
- **Determinism:** render the same shot twice → identical canonical form.
- **Bounds handling:** bounds absent from canonical form; present in JSON only with `--bounds`.
- **Diff:** known before/after trees → expected `+`/`-`/`~` delta lines; equal trees → `match`.
- **CLI single:** `--semantics=json` and `=text` output; sidecar files with `--out`;
  `--verify-semantics` match/mismatch/missing-golden and exit codes; existing flags unchanged.
- **CLI batch:** `report.json` `schemaVersion == 2`; dump embedded only with `--semantics`;
  semantics mismatch drives exit 1 and `totals.semanticsMismatched`; `events.ndjson` line
  count and content with `--events`.
- **Back-compat:** a v1-shaped consumer still parses (new fields nullable/defaulted); pixel
  verify path and totals unchanged when semantics flags are off.
- **Publish:** `publishToMavenLocal` produces the artifact + POM + sources/javadoc jars
  under the reconciled coordinate.
