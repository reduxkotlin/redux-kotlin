# redux-kotlin-snapshot: semantics dump, semantics-golden, publish — design

**Date:** 2026-07-01
**Module:** `redux-kotlin-snapshot` (+ its `rk snapshot` / `runCli` surface)
**Status:** design, pending approval (revised after multi-lens review)

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

- **A.** Real semantics extraction (the P0 gap).
- **B.** Canonical form + line-diff delta.
- **C.** CLI surface for semantics (single + batch), exit-code contract preserved.
- **D.** Batch report v2 + terse drift-only output + measurement fields.
- **E.** Maven Central publish + version reconciliation.

**Cut from an earlier draft (deliberately out of scope):** a live-view emission seam
(`onShot` callback / `events.ndjson`). `BatchRunner.run` already produces `ShotReport`s
one at a time, so a future live-view watcher can add that hook as a small, localized change
when it is actually built. Not paid for now.

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
    (public), `.id`, `.isRoot`. (`boundsInRoot`/`boundsInWindow` exist but are unused — see §A.)
  - `SemanticsConfiguration` is iterable and exposes `get`/`contains` by key.
  - `SemanticsProperties.Text` (`List<AnnotatedString>`), `.ContentDescription`
    (`List<String>`), `.Role` (`Role`), `.TestTag` (`String`), plus `Disabled`, `Selected`,
    `ToggleableState`.
  - `ImageComposeScene` is **not** deprecated (only its old `render(Duration)` overload is);
    no migration to `androidx.compose.ui.scene.ComposeScene` needed.
- **`explicitApi()` is on (strict)** via `convention.publishing-jvm`, with ABI validation
  tracked in `redux-kotlin-snapshot/api/redux-kotlin-snapshot.api`. Every new public
  declaration MUST carry an explicit `public` modifier and KDoc, or the build fails and the
  shipped javadoc jar is empty. New public API also requires an `apiDump` update.
- **Publishing is already wired to Maven Central.** `convention.publishing-jvm` applies
  `com.vanniktech.maven.publish` with `publishToMavenCentral(automaticRelease = true)` and
  `signAllPublications()` (CI-only, gated on `signingInMemoryKey`). Coordinates
  `org.reduxkotlin:redux-kotlin-snapshot:<version>`; `version`/`group` inherited from root
  (`1.0.0-SNAPSHOT`, `org.reduxkotlin`). Release tags set `-Pversion=<tag>` (CLI is at
  `1.0.0-alpha02`).

## A. Semantics extraction

New file `Semantics.kt`. The `SemanticsDump` scaffold in `RenderBackend.kt` is expanded.
All declarations below are `public` with KDoc.

### Data model

`SemanticsDump.Node` is nested to avoid clashing with Compose's
`androidx.compose.ui.semantics.SemanticsNode`. **No bounds field** — spatial/positional
debugging lives in the PNG; excluding position keeps one serialization form and removes the
cross-arch (arm64 vs x64 rounding) determinism footgun entirely.

```
@Serializable
public class Node(
    public val role: String?,                  // Role.toString() lowercased; null if absent
    public val text: List<String>,             // AnnotatedString.text on this node, in order
    public val contentDescription: List<String>,
    public val testTag: String?,
    public val enabled: Boolean?,              // false when SemanticsProperties.Disabled present; else null (tri-state: never true)
    public val selected: Boolean?,             // SemanticsProperties.Selected; null if absent
    public val toggle: String?,                // ToggleableState name (On/Off/Indeterminate); null if absent
    public val children: List<Node>,
)

public class SemanticsDump(
    public val roots: List<Node>,              // one per SemanticsOwner (see multi-owner); empty when nothing captured
    public val texts: List<String>,            // flat, pre-order — kept for back-compat
) {
    public fun toText(): String                // compact indented tree — DEFAULT agent/human form
    public fun toCanonicalJson(): String       // the single JSON form: used for --semantics-format json AND for goldens
    public companion object { public val EMPTY: SemanticsDump = SemanticsDump(roots = emptyList(), texts = emptyList()) }
}
```

`RenderResult(png, semantics)` shape is unchanged; `semantics` is now populated. There is
**one** JSON serialization (`toCanonicalJson`) — no separate `toJson`; the CLI json mode and
the golden both use it, so canonical == report-embedded == emitted JSON.

### Extraction

In `ImageComposeSceneBackend.render`, after `scene.render()` and before `scene.close()`:

1. Read `scene.semanticsOwners`.
2. **Multi-owner:** a scene may produce more than one owner (a Popup/Dialog gets its own).
   Map each owner's `rootSemanticsNode` to one entry in `SemanticsDump.roots`. Sort owners
   deterministically by `(root.id ascending)` — `id` is a stable monotonic assignment within
   a composition and does not depend on arch/pixels (unlike bounds). During implementation,
   first confirm headless `ImageComposeScene` actually populates a second owner for a
   Popup/Dialog; if it does not, `roots` simply has one entry and the sort is a no-op — the
   list model costs nothing and stays correct either way.
3. For each root, take the **merged** tree (matches the compose-ui-test default, carries
   `Role`). Recurse `node.children` (public, layout order — deterministic), reading
   `node.config[SemanticsProperties.*]`.
4. Build the flat `texts` list from a pre-order walk (back-compat with the current field).

Requires only `@OptIn(ExperimentalComposeUiApi::class)`.

**Merged-tree caveat (documented + tested):** merged traversal collapses a descendant's
semantics onto the merge boundary — e.g. a `Button` absorbs its child `Text`, so the `Text`
appears in the button node's `text` and the child `Node` disappears from `children`. This is
intended (it matches accessibility/testing semantics) but must be documented so consumers do
not expect a 1:1 composable→node tree. `enabled` is tri-state: `false` or `null`, never
`true` (Compose only records the disabled marker).

### Error handling (non-fatal)

The semantics read uses experimental API. If any step from `semanticsOwners` through
`config` throws between `scene.render()` and `scene.close()`, **degrade to
`SemanticsDump.EMPTY` and keep the already-rendered PNG** — the shot is not failed. Rationale:
pixels are the primary artifact; a semantics-extraction hiccup must not regress the existing
PNG contract. (A `--verify-semantics` run against an EMPTY dump is a `mismatch`, which surfaces
the problem without crashing the render.)

## B. Canonical form + delta

New file `SemanticsDiffer.kt`.

- **Canonical form** = `SemanticsDump.toCanonicalJson()`: deterministic pretty-printed JSON of
  the `roots` tree, stable field order (data-class order via kotlinx JSON), `encodeDefaults`
  consistent. This string is what a semantics golden stores.
- **Verdict** = pure string equality of golden vs actual canonical form: `match` when equal,
  else `mismatch`; `missing-golden` when the file is absent (mirrors pixel verify).
- **Delta (line diff):** on `mismatch`, render a readable unified line diff of the two
  pretty-printed canonical blobs (added/removed/context lines). Cap at a configured max line
  count with an `… (N more)` overflow marker so a large subtree change stays bounded. A
  structured per-node path-walk differ is **deferred** — the gate is string equality and the
  line diff is a readable-enough signal; add the field-level walker only if a consumer needs
  it.

Verdict strings are lowercase (`match` | `mismatch` | `missing-golden`) to match the existing
report `result` vocabulary.

## C. CLI surface (`cli/Cli.kt`)

Existing flags, `--list` shape, batch manifest, and exit codes (0 ok / 1 render-or-verify
failure / 2 usage) are unchanged. `runCli` → `exitProcess(0)` contract preserved. Clikt has
no native optional-value option, so each capability is a **distinctly declared** option, not a
`[=value]` flag.

### Single shot (`runSingle`)

- `--semantics` (bare flag): after render, emit the dump. To stdout by default; when `--out`
  is set, also write a sidecar next to it (`<out>.semantics.<ext>`). Routing matrix stated in
  §D.
- `--semantics-format json|text` (default `text`): selects `toText()` vs `toCanonicalJson()`
  for the emitted dump. `text` is the default because the token win the value prop rests on is
  the compact text form.
- `--verify-semantics-file <file>`: compare the rendered canonical form against this golden
  file; print `verify-semantics: <verdict>` and, on mismatch, the line-diff delta; exit 1 on
  mismatch. Independent of the pixel `--verify`.
- `--update-semantics`: write/overwrite the canonical form to the `--verify-semantics-file`
  path (single) — the baseline-authoring path. Without `--verify-semantics-file` this is a
  usage error (exit 2).

### Batch (`runBatch`)

- `--semantics`: write a per-shot sidecar (`<id>.semantics.txt` by default, `.json` per
  `--semantics-format`) beside the shot's PNG in `outDir`. The full dump is **not** embedded
  inline in `report.json` for every shot (keeps the report small); the report carries the
  verdict, delta, `semanticsBytes`, and the sidecar path (§D). This is the read-on-demand
  model: the agent reads a sidecar only for a drifted shot.
- `--verify-semantics` (bare flag): enable the semantics-golden gate. Requires `--golden-dir`
  (else exit 2 with a fix-it message, same style as the pixel path). Goldens are sidecars in
  `--golden-dir` as `<id>.semantics.json`, beside `<id>.png`. A semantics mismatch counts as a
  failure (exit 1), delta recorded in the report. A missing semantics golden is
  `missing-golden` (non-failing, like pixels).
- `--update-semantics`: (re)write each shot's canonical form to
  `<golden-dir>/<id>.semantics.json` and exit 0 without gating — regenerate baselines after an
  intended UI change. Requires `--golden-dir`.

**Precedence with `--json`:** `--json` still emits the machine report to stdout; with
`--semantics`, dumps go to sidecar files (never interleaved into the `--json` stdout stream).

## D. Batch report (`Report.kt`) + output

`schemaVersion` bumped 1 → 2. All new fields nullable/defaulted so a v1 reader with
`ignoreUnknownKeys` still parses a v2 file and a v2 reader parses a v1 file (both directions
required; the module's `Json` config must set `ignoreUnknownKeys = true`).

- `ShotReport.verifySemantics: SemanticsVerifyReport? = null` — present only with
  `--verify-semantics`. `SemanticsVerifyReport(golden: String, result: String, delta: List<String>? = null)`.
- `ShotReport.semanticsSidecar: String? = null` — sidecar path, present with `--semantics`.
- `ShotReport.semanticsBytes: Int? = null` — size of the canonical dump, for the dump-vs-PNG
  saving comparison (`bytes` for PNG already exists).
- `Totals` gains, all `= 0`: `semanticsMismatched`, `semanticsMissingGolden`,
  `semanticsMatched`, and `renderMsTotal: Long = 0` (aggregate of the per-shot `renderMs` the
  runner already computes).
- **`Totals.ok` redefined** to exclude semantics failures too:
  `status == "ok" && verify?.result != "mismatch" && verifySemantics?.result != "mismatch"`
  (today a pixel-pass/semantics-fail shot is wrongly counted in both `ok` and
  `semanticsMismatched`).
- Batch gate: `failed > 0 || mismatched > 0 || semanticsMismatched > 0`.

**Terse drift-only stdout (default, non-`--json`):** the batch summary line stays, but is
followed by one line per *drifted* shot only: `id`, pixel verdict, semantics verdict, and the
`png` / `diff` / `semanticsSidecar` paths, then the (capped) delta. The happy path prints just
the summary; the agent never parses `report.json` unless something drifted.

**Dashboard:** `DashboardGenerator` (behind the existing `--dashboard` flag) is updated to
reflect the semantics verdict and the new totals, so a semantics-only mismatch does not render
as a green card while the process exits 1.

**Wiring:** `BatchRunner.run` gains `semantics: Boolean` and `verifySemantics: Boolean`
parameters (parallel to the existing `verify`), consumes `renderResult().semantics` (currently
discarded at `BatchRunner.kt:57`), and takes an injected `SemanticsDiffer` beside `Differ`.

## E. Publishing / version reconcile

Infra already targets Central; the work is cutting a **pinnable released version** and
reconciling the skew.

- **Single version line.** The snapshot module rides the same release-tag flow as the CLI.
  The next release cuts `org.reduxkotlin:redux-kotlin-snapshot:1.0.0-alpha04` to Maven
  Central (from the `-Pversion=1.0.0-alpha04` tag build). `1.0.0-SNAPSHOT` stays the `master`
  development baseline.
- **Consumer docs.** Add a module README section: the pinned coordinate, the `repositories {}`
  (Maven Central) + `dependencies {}` snippet, and a note that scene authoring also needs the
  transitive Compose/clikt/serialization `api` deps.
- **Deliverable boundary.** The signed publish runs in CI on a maintainer tag push (signing
  secrets, irreversible). This work delivers: (1) confirmation the module is in the release
  publish set, (2) version reconciliation + docs, (3) `apiDump` for the new public API, and
  (4) local `./gradlew :redux-kotlin-snapshot:publishToMavenLocal` verification that the
  artifact + POM + sources/javadoc jars resolve. The tag push itself is a follow-up maintainer
  action, noted in the plan.

## Documentation (acceptance deliverables)

- Module README: **Flags table** updated for `--semantics`, `--semantics-format`,
  `--verify-semantics` / `--verify-semantics-file`, `--update-semantics`; a **Typical agent
  loop** section showing generate-baseline → verify → read-sidecar-only-on-drift; the consumer
  coordinate + Gradle snippet (§E).
- The **dump JSON schema** and the **report v2 schema** documented explicitly (the report
  types are `internal`, so the JSON is the only consumer surface).
- A **worked agent-loop example** (commands + sample terse output + sample sidecar).
- KDoc on all new public API (required by `explicitApi()` strict).

## Non-goals

- Keep the render backend JVM/Skiko headless. No emulator/Robolectric.
- Headless render has no async image loading — out of scope.
- No live-view seam / web server / SSE now (see Scope).
- No unmerged-semantics tree (merged only); revisit if a consumer needs it.
- No structured per-node path-walk differ (line diff only); revisit if a consumer needs it.
- No `bounds` in the dump.

## Testing

- **Extraction correctness:** a scene with `Text`, a `Button` (Role), a `testTag`, a disabled
  node, and a selected node → assert the `Node` tree fields and pre-order `texts`.
- **Merged-tree absorption:** assert a `Button`+`Text` collapses to one node whose `text`
  holds the label and whose `children` omit the absorbed `Text`.
- **`enabled` tri-state:** assert `false`/`null` only, never `true`.
- **Determinism:** render the same shot twice → identical `toCanonicalJson()`.
- **Multi-owner:** a Popup/Dialog fixture → assert `roots` ordering is stable by `root.id`
  (with the tie-break defined); if headless render does not populate a second owner, assert the
  single-root fallback instead and record that finding.
- **Empty / text-free:** the counter scene (no text) → pin the exact dump shape (`roots` shape,
  `texts == emptyList()`); assert its text-bearing semantics fixture is verified via the
  canonical form only, never a committed `*.png` golden (font-dependent, flakes cross-arch).
- **Diff:** known before/after canonical blobs → expected line-diff delta and overflow cap;
  equal blobs → `match`.
- **CLI single:** `--semantics` + `--semantics-format text|json` to stdout and to sidecar with
  `--out`; `--verify-semantics-file` match/mismatch/missing-golden and exit codes;
  `--update-semantics` writes the baseline; missing `--verify-semantics-file` → exit 2.
- **CLI batch:** `report.json` `schemaVersion == 2`; sidecars written with `--semantics`;
  `--verify-semantics` without `--golden-dir` → exit 2; semantics mismatch drives exit 1,
  `totals.semanticsMismatched`, and the corrected `totals.ok`; terse drift-only stdout lists
  only drifted shots; `--update-semantics` regenerates goldens and exits 0.
- **Back-compat:** a v1-shaped `report.json` parses under the v2 model (`ignoreUnknownKeys`);
  the pixel verify path, totals, and exit codes are unchanged when semantics flags are off.
- **Measurement:** `semanticsBytes` populated; `Totals.renderMsTotal` aggregates per-shot
  `renderMs`; `semanticsMatched`/gated-out counts correct.
- **Publish:** `publishToMavenLocal` produces the artifact + POM + sources/javadoc jars under
  the reconciled coordinate; `apiDump` matches.
