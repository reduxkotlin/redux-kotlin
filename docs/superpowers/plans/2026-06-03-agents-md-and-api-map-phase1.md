# Phase 1 — AGENTS.md + API-map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Assemble the T0 layer — `AGENTS.md` (repo-root, token-tight agent index) + `docs/agent/api-map.md` (module → `.api` index) — from existing canon, single-source (point, don't duplicate).

**Architecture:** Docs-only. Two files. Content derived from `CLAUDE.md` (modules/commands), `examples/taskflow/ARCHITECTURE.md` §17 (Rules C–H), `docs/agent/references/` (T1 pointers), and `*/api/*.klib.api` (the 19 committed dumps). Gate = reference integrity (every cited path/module/command resolves).

**Tech Stack:** Markdown; bash/grep for the gate.

---

## Task 1: `docs/agent/api-map.md`

**Files:** Create `docs/agent/api-map.md`

- [ ] **Step 1:** Enumerate the committed dumps: `ls */api/*.klib.api` (expect 19). 
- [ ] **Step 2:** Write `docs/agent/api-map.md` with provenance frontmatter (`tier: T2`, `concern: api-map`, `derives_from:` the dump glob, `rules: []`, `assembles_into: [AGENTS.md, claude-skill]`, `last_verified: {commit: <git rev-parse --short HEAD>, date: 2026-06-03}`) and a markdown table with one row per dump: **module** (the `:name` from `settings.gradle.kts`) · **`.api` path** (e.g. `redux-kotlin/api/redux-kotlin.klib.api`) · **what's in it** (one line; for the 9 core modules use CLAUDE.md's descriptions; for routing/bundle/bom/devtools/codegen give a terse one-liner inferred from the module name + a peek at the dump). Add a note: `examples/*` are `convention.control` (no `.api`); read source/`ARCHITECTURE.md` for those.
- [ ] **Step 3:** Verify every path in the table exists: `for f in $(grep -oE '[a-z0-9-]+/api/[a-z0-9-]+\.klib\.api' docs/agent/api-map.md); do test -f "$f" || echo "MISS $f"; done; echo done` → no MISS.
- [ ] **Step 4:** Commit: `git commit -am "docs(agent): add API-map index (T2)"`

---

## Task 2: `AGENTS.md` (repo root)

**Files:** Create `AGENTS.md`

Read first: `CLAUDE.md`, `examples/taskflow/ARCHITECTURE.md` §17 (lines ~866–886, Rules **C–H** — there are no named A/B; do not invent them), `docs/agent/references/README.md`.

- [ ] **Step 1:** Write `AGENTS.md` (repo root), token-tight (~120–180 lines), these sections:
  1. **One-paragraph "what redux-kotlin is"** — KMP port of the Redux contract; minimal core + opt-in companion modules on one `Store<S>` contract.
  2. **Module map** — the 9 core published modules with the one-line "use this for X" from `CLAUDE.md`; one line noting additional modules (routing, bundle, bom, devtools, codegen) exist — see `docs/agent/api-map.md`. `examples/` = samples (taskflow is the canonical app).
  3. **Build / test / lint** — the four gate commands (`./gradlew build`, `./gradlew detektAll`, `./gradlew apiCheck`, `./gradlew :<module>:jvmTest`) each one line; pointer: "full gate detail → `CLAUDE.md`". State the hard rule: never `--no-verify`; `explicitApi()` requires KDoc on every public decl.
  4. **Design rules** — Rules **C–H** as one line each, verbatim-faithful to ARCHITECTURE §17 (C render isolation · D identity split · E off-main effects · F delta-only status · G mint-at-edge · H single inset point). Pointer: full text → `examples/taskflow/ARCHITECTURE.md` §17.
  5. **Where things live** — the recommended app layout is **package-by-feature** (strategy §5.1): `feature/<name>/` slice + shared `core` (kernel) · `infra` · `app` · `ui`; the canonical example is `examples/taskflow`. One line.
  6. **Deeper knowledge (T1/T2)** — point into `docs/agent/references/` (list the 7 guides via the README, noting feature-slice.md is live, others planned) and T2 (`examples/taskflow/ARCHITECTURE.md`, `docs/agent/api-map.md`).
  Use plain `` `path` `` references (backtick-wrapped, no line numbers). Keep prose to the minimum an index needs.
- [ ] **Step 2:** Length check: `wc -l AGENTS.md` ≤ ~180. If over, cut prose (it's an index).
- [ ] **Step 3:** Commit: `git commit -am "docs(agent): add AGENTS.md T0 index"`

---

## Task 3: Reference-integrity gate (acceptance)

**Files:** none.

- [ ] **Step 1:** Run from worktree root:
```bash
bash -c '
miss=0
# all backtick `path` and `path/api/*.klib.api` refs in both files exist
for F in AGENTS.md docs/agent/api-map.md; do
  for p in $(grep -oE "[A-Za-z0-9_./-]+/[A-Za-z0-9_./-]+\.(md|api|kts)" "$F" | sort -u); do
    [ -e "$p" ] || { echo "MISS path ($F): $p"; miss=1; }
  done
done
# referenced docs exist
for p in docs/agent/references/feature-slice.md docs/agent/references/README.md examples/taskflow/ARCHITECTURE.md CLAUDE.md docs/agent/api-map.md; do
  test -e "$p" || { echo "MISS doc: $p"; miss=1; }
done
# the 9 core module names resolve in settings.gradle.kts
for m in redux-kotlin redux-kotlin-threadsafe redux-kotlin-concurrent redux-kotlin-granular redux-kotlin-registry redux-kotlin-multimodel redux-kotlin-multimodel-granular redux-kotlin-compose redux-kotlin-compose-multimodel; do
  grep -q "\":$m\"" settings.gradle.kts || { echo "MISS module: $m"; miss=1; }
done
[ "$miss" = 0 ] && echo "REFS RESOLVE OK" || echo "REFS FAILED"
'
```
Expected: `REFS RESOLVE OK`. Fix any MISS (correct the path/name in the doc), re-run, commit fixes.
- [ ] **Step 2:** No-collateral: `git diff --name-only origin/master...HEAD` shows only `AGENTS.md`, `docs/agent/`, `docs/superpowers/`. No `.kt`/`website/`/`examples/`.

---

## Task 4: PR

- [ ] **Step 1:** `git push -u origin agents-md-phase1`
- [ ] **Step 2:** `gh pr create --base feature-slice-pilot --head agents-md-phase1` (stacked on the pilot) — title `docs(agent): AGENTS.md (T0) + API-map index (Phase 1)`; body: assembled-from-canon, single-source (points into refs/CLAUDE.md), the reference-integrity gate result, merge-after-#313 note, link the spec.

---

## Self-review notes
- Spec coverage: api-map (T1), AGENTS.md (T2), gate (T3), PR (T4). All spec deliverables mapped.
- Rules: C–H only (no A/B) — plan instructs not to fabricate.
- Module scope: api-map covers all 19 dumps; AGENTS module-map highlights the core 9 + pointer.
- No duplication of CLAUDE.md — AGENTS.md references it.
