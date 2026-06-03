# Feature-Slice Reference Pilot — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Author the first T1 agent-reference guide (`docs/agent/references/feature-slice.md`) and lock the reference-set conventions (provenance header, `path → symbol` anchor style, template, index) that Phase 0-rest's six remaining guides will replicate.

**Architecture:** Documentation-only sub-project. Three markdown files under a new `docs/agent/references/` tree, derived from the merged package-by-feature `examples/taskflow` (Phase 0a). No source or build changes. The acceptance gate is **citation integrity** — every backtick-wrapped `` `path → symbol` `` anchor resolves (path exists + symbol present) — implemented as a grep sweep that doubles as a manual dry-run of the Phase-3 L4 anchor checker.

**Tech Stack:** Markdown; bash/grep for the citation-integrity gate. Source-of-truth being documented: Kotlin Multiplatform taskflow (`core/ infra/ feature/ app/ ui/`), Rules A–H (`examples/taskflow/ARCHITECTURE.md`), redux-kotlin library `.api` dumps.

---

## Conventions locked by this pilot (every guide inherits these)

- **Anchor form:** all source citations are backtick-wrapped `` `<repo-relative-path> → <Symbol>[, <Symbol>...]` ``. Machine-extractable (a literal ` → ` separator inside backticks). Paths are repo-relative (start `examples/...` or `redux-kotlin.../...`). No line numbers (drift-resilient; the Phase-0a lesson).
- **Provenance header:** YAML frontmatter, fields `tier, concern, derives_from, api_files, rules, assembles_into, last_verified{commit,date}` (see spec).
- **Location:** `docs/agent/references/`. Out of `website/` and `.claude/`.
- **Tone/length:** tight, scannable, citation-dense; ~250–400 lines; prose carries the *why/rule*, anchors carry the *where*.

## File structure

| File | Responsibility |
|---|---|
| `docs/agent/references/_template.md` | Frontmatter + section skeleton + the anchor-form rule, for the other six guides to copy. |
| `docs/agent/references/README.md` | Index of the seven T1 guides with status; the cross-reference hub. |
| `docs/agent/references/feature-slice.md` | The worked guide: how to add a feature slice, anchored on `feature.boardlist` with `feature.board` callouts. |

Base revision for `last_verified`: `06214a9` (this worktree's `master` base). Confirm with `git rev-parse --short HEAD` before writing headers; use that value.

---

## Task 1: Lock the template (`_template.md`)

**Files:** Create `docs/agent/references/_template.md`

- [ ] **Step 1: Confirm base commit for provenance headers**

Run: `git rev-parse --short HEAD`
Expected: `06214a9` (or the current base — use whatever it prints in all `last_verified.commit` fields below).

- [ ] **Step 2: Write `_template.md`**

Create the file with this exact content (it is both the copy-source for future guides AND itself a valid, citation-free skeleton):

````markdown
---
tier: T1
concern: <kebab-concern-name>
derives_from:
  # backtick-free here; each entry is `<repo-relative-path> → <Symbol>[, <Symbol>]`
  - <path> → <Symbol>
api_files:
  # committed library .api dumps backing the APIs this guide uses (taskflow itself has none)
  - <module>/api/<module>.klib.api
rules: []          # subset of A–H this guide enforces
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: <short-sha>, date: <YYYY-MM-DD> }
---

# <Title>

> One-line statement of what this guide answers.

## <Section>

Prose explains the *why* and the *rule*. Every source reference uses the anchor form
`` `path → Symbol` `` — repo-relative path, a literal ` → `, then the symbol(s). Example:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → boardListReducer`.

## See also

- Cross-links to sibling guides via [README](./README.md).
````

- [ ] **Step 3: Verify it has no dangling anchors**

Run: `grep -oE '\`[^\`]+ → [^\`]+\`' docs/agent/references/_template.md || echo "no concrete anchors (template is a skeleton) — OK"`
Expected: the only ` → ` occurrences are the illustrative one in prose; the template is not citation-checked (it is a skeleton). OK either way.

- [ ] **Step 4: Commit**

```bash
git add docs/agent/references/_template.md
git commit -m "docs(agent): add reference-set template + anchor conventions"
```

---

## Task 2: Index (`README.md`)

**Files:** Create `docs/agent/references/README.md`

- [ ] **Step 1: Write `README.md`**

Create with this content:

```markdown
# redux-kotlin agent reference set (T1)

Single-source, per-concern guides for building with redux-kotlin. `AGENTS.md` (Phase 1) and the
Claude skill (Phase 2) assemble from these — edit knowledge here, not in copies.

Anchor convention: source references are written `` `repo-relative-path → Symbol` `` and are
checked to resolve (path exists, symbol present). See [_template.md](./_template.md).

| Concern | Guide | Status |
|---|---|---|
| Add a feature slice | [feature-slice.md](./feature-slice.md) | ✅ |
| Store setup & topology | `store-setup.md` | planned (0-rest) |
| Compose binding (Rule C) | `compose-binding.md` | planned (0-rest) |
| Effects + sync (Rule E) | `effects-sync.md` | planned (0-rest) |
| Testing & the verify loop | `testing.md` | planned (0-rest) |
| The 6 platform shims | `platform-shims.md` | planned (0-rest) |
| Modularization | `modularization.md` | planned (0-rest) |

Tiers: **T0** = rules card + module map + commands (assembled into `AGENTS.md`). **T1** = these guides.
**T2** = [`examples/taskflow/ARCHITECTURE.md`](../../../examples/taskflow/ARCHITECTURE.md) + committed `.api` dumps.
```

- [ ] **Step 2: Verify the two relative links resolve**

Run:
```bash
test -f examples/taskflow/ARCHITECTURE.md && echo "ARCHITECTURE OK"
# the planned-guide names are intentionally NOT links yet (files don't exist) — confirm none are bracketed links:
grep -E '\[`?(store-setup|compose-binding|effects-sync|testing|platform-shims|modularization)\.md' docs/agent/references/README.md && echo "ERROR: planned guide is a live link" || echo "planned guides are plain code spans — OK"
```
Expected: `ARCHITECTURE OK` and `planned guides are plain code spans — OK`.

- [ ] **Step 3: Commit**

```bash
git add docs/agent/references/README.md
git commit -m "docs(agent): add reference-set index"
```

---

## Task 3: Author `feature-slice.md`

**Files:** Create `docs/agent/references/feature-slice.md`

Read the real sources first so every anchor is accurate (do not invent symbols):
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/` →
`feature/boardlist/*`, `feature/board/{EffectsMiddleware.kt,BoardSelectors.kt,BoardReducers.kt,BoardScreen.kt}`,
`core/{Action.kt,CardActions.kt,BoardEntities.kt}`, `app/{AccountStore.kt,AppStore.kt}`, and
`examples/taskflow/ARCHITECTURE.md` (Rules A–H wording). Find one existing reducer test under
`examples/taskflow/composeApp/src/commonTest/.../feature/board/` to cite for the Tests element.

- [ ] **Step 1: Write the frontmatter**

Use the locked header. Confirmed-resolving `derives_from` anchors (verified against base `06214a9`):

```yaml
---
tier: T1
concern: feature-slice
derives_from:
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → BoardListModel, boardListReducer, BoardListScreen, CreateBoard, BoardSummaryCard
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/EffectsMiddleware.kt → effectsMiddleware
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardSelectors.kt → deriveVisibleCardIds
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/Action.kt → Action, Undoable
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/CardActions.kt → InverseOp
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → declareAccountModels
api_files:
  - redux-kotlin-compose-multimodel/api/redux-kotlin-compose-multimodel.klib.api
  - redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api
rules: [C, E, G]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 06214a9, date: 2026-06-03 }
---
```

- [ ] **Step 2: Write the intro + "where things live"**

A one-paragraph definition of a feature slice in this codebase, then a mini-map. Content (write as prose + a list; anchors backtick-wrapped):
- Slice = one directory `feature/<name>/` co-locating that feature's slot model, actions, reducer, effect handler, screen, selectors, tests.
- Shared homes: `core` (domain kernel — entities + `Action`/`Undoable` markers + card/sync-contract actions), `infra` (platform shims, db, data/sync, util), `app` (composition root: `App.kt`, store factories, `app/nav`), `ui` (theme + cross-feature widgets).
- State the dependency direction: `core ← infra`; `core/infra/ui ← feature/*`; everything `← app`.

- [ ] **Step 3: Write the seven slice elements**

One subsection per element. Each: *what · where it lives · the Rule it honors · a `` `path → Symbol` `` anchor* (boardlist spine; board callout where noted). Required anchors (all verified to resolve):

1. **Models** — `` `…/feature/boardlist → BoardListModel` `` (slot model) + `` `…/core/BoardEntities.kt → BoardSummary` `` (kernel entity).
2. **Actions** — `` `…/feature/boardlist → CreateBoard, LoadBoardListSucceeded` `` (feature leaves) + `` `…/core/Action.kt → Action, Undoable` `` + `` `…/core/CardActions.kt → InverseOp` ``. Include the **plain-marker rationale**: `Action`/`Undoable` are plain interfaces, not `sealed`, because Kotlin requires sealed subtypes in the same package; package-by-feature spreads leaves across packages. `InverseOp` stays `sealed` (leaves co-located in `core`). Behavior-preserving: every `when(action)` uses `else`.
3. **Reducer** — `` `…/feature/boardlist → boardListReducer` ``. Note the hand-written `model<M> { on<T> { … } }` routing DSL and **Rule G** (mint ids/timestamps at the dispatch edge, never in the reducer).
4. **Effects** — **Rule E**: effects originate only in middleware; the feature's handler is composed into `` `…/feature/board/EffectsMiddleware.kt → effectsMiddleware` ``, runs off-main. (boardlist's load is handled there → board callout.)
5. **Screen** — **Rule C** render isolation; bind narrowest slice via `selectorState`/`fieldState`. `` `…/feature/boardlist → BoardListScreen` `` (+ `` `…/feature/board/BoardScreen.kt → BoardScreen` `` callout).
6. **Selectors** — pure derivation. `` `…/feature/board/BoardSelectors.kt → deriveVisibleCardIds` `` (board callout; boardlist derives inline).
7. **Tests** — `commonTest` default, `jvmTest` for jvm-only. Cite `` `…/commonTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardReducersTest.kt → BoardReducersTest` ``.

- [ ] **Step 4: Write store wiring + verify loop + codegen note + cross-refs**

- **Store wiring:** register the slot (order matters) and compose the effect handler (order `activityLogger → undo → effects`). Anchors `` `…/app/AccountStore.kt → declareAccountModels` `` and `` `…/app/AppStore.kt → createAppStore` ``.
- **Verify loop (brief):** `compile <target> → commonTest/jvmTest → detektAll → apiCheck`; iOS-sim host-gating caveat (trust CI). One line: full treatment → `testing.md`.
- **Codegen note (brief):** KSP `@Reduce` is packaging-agnostic. One line: full treatment → feature/testing guides.
- **See also:** link `[README](./README.md)` for sibling guides.

- [ ] **Step 5: Commit**

```bash
git add docs/agent/references/feature-slice.md
git commit -m "docs(agent): add feature-slice T1 reference guide"
```

---

## Task 4: Citation-integrity gate (acceptance test + L4 dry-run)

**Files:** none committed (verification only; this logic becomes the Phase-3 anchor checker later).

- [ ] **Step 1: Run the anchor sweep**

Extract every backtick-wrapped `path → symbol` anchor from the guide AND the frontmatter `derives_from`, and verify each resolves. Run from the worktree root:

```bash
GUIDE=docs/agent/references/feature-slice.md
fail=0
# 1) inline backtick anchors: `path → Sym, Sym`
grep -oE '`[^`]+ → [^`]+`' "$GUIDE" | sed 's/^`//; s/`$//' | while IFS= read -r anchor; do
  path="${anchor%% → *}"; syms="${anchor##* → }"
  [ -e "$path" ] || { echo "MISS path: $path"; continue; }
  IFS=',' read -ra arr <<< "$syms"
  for s in "${arr[@]}"; do s="$(echo "$s" | xargs)"; grep -rq "$s" "$path" || echo "MISS symbol: $s in $path"; done
done
# 2) frontmatter derives_from entries: "  - path → Sym, Sym"
sed -n '/^derives_from:/,/^[a-z_]*:/p' "$GUIDE" | grep -E '→' | sed 's/^[[:space:]]*-[[:space:]]*//' | while IFS= read -r anchor; do
  path="${anchor%% → *}"; syms="${anchor##* → }"
  [ -e "$path" ] || { echo "MISS df-path: $path"; continue; }
  IFS=',' read -ra arr <<< "$syms"
  for s in "${arr[@]}"; do s="$(echo "$s" | xargs)"; grep -rq "$s" "$path" || echo "MISS df-symbol: $s in $path"; done
done
# 3) api_files exist
sed -n '/^api_files:/,/^[a-z_]*:/p' "$GUIDE" | grep -E '\.api' | sed 's/^[[:space:]]*-[[:space:]]*//' | while IFS= read -r f; do
  test -f "$f" || echo "MISS api_file: $f"; done
echo "sweep done"
```
Expected: only `sweep done` with **no `MISS` lines**.

- [ ] **Step 2: Fix any MISS**

For each `MISS`, correct the anchor in `feature-slice.md` to the real path/symbol (read the source to find the right one). Re-run Step 1 until clean. Commit fixes if any:

```bash
git commit -am "docs(agent): fix feature-slice anchors to resolve"
```

- [ ] **Step 3: Markdown link + base-commit checks**

Run:
```bash
# intra-docs links resolve (README/template/guide + ARCHITECTURE/.api relative paths)
test -f docs/agent/references/README.md && test -f docs/agent/references/_template.md && echo "set present"
grep -q "$(git rev-parse --short HEAD)" docs/agent/references/feature-slice.md && echo "last_verified matches base" || echo "WARN: bump last_verified.commit"
```
Expected: `set present` and `last_verified matches base`.

---

## Task 5: Final read-through + no-collateral check

**Files:** none.

- [ ] **Step 1: Confirm no website/source impact**

Run:
```bash
git diff --name-only origin/master...HEAD
```
Expected: only files under `docs/agent/references/` and `docs/superpowers/` (spec + plan). No `website/`, no `examples/`, no `*.kt`.

- [ ] **Step 2: Length/tone check**

Run: `wc -l docs/agent/references/feature-slice.md`
Expected: roughly 250–400 lines. If far outside, tighten (over) or deepen (under) — prose carries why/rule, anchors carry where.

- [ ] **Step 3: Read the guide top-to-bottom** as a cold agent: can you build a new feature slice from it alone, following each anchor? Fix gaps; re-run Task 4 Step 1 if any anchor changed.

---

## Task 6: Open PR

**Files:** none.

- [ ] **Step 1:** `git push -u origin feature-slice-pilot`
- [ ] **Step 2:** `gh pr create --base master --title "docs(agent): feature-slice reference guide + reference-set conventions (Phase 0-pilot)" --body-file <body>` — body summarizes: Phase 0-pilot, the three files, the locked conventions (provenance header, `path → symbol` anchors, location), the citation-integrity gate result, and that 0-rest replicates the template across the remaining six guides. Link the spec.

---

## Self-review notes

- **Spec coverage:** `_template.md` (Task 1) = convention lock; `README.md` (Task 2) = index deliverable; `feature-slice.md` (Task 3) = the guide with all seven elements + wiring/verify/codegen/mini-map; citation-integrity gate (Task 4) = the spec's acceptance gate + L4 dry-run; no-collateral (Task 5) = "out of scope: no source/website changes". All spec deliverables mapped.
- **No placeholders:** every file's content is given inline; the only intentional "fill from source" is Task 3's Tests anchor (the real test filename) and AppStore's top-level symbol — both with explicit "verify the real symbol" instructions and caught by the Task 4 sweep.
- **Anchor consistency:** the same backtick `path → Symbol` form is used in `_template.md`, the guide, and the Task 4 extractor (it greps exactly that form). Frontmatter `derives_from` uses the un-backticked `- path → Symbol` YAML form, which the extractor handles separately.
- **Drift-resilience:** no line numbers anywhere; `last_verified.commit` pins the revision.
