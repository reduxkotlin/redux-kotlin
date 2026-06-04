# Phase 4 — Knowledge-Sync Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Add an advisory LLM workflow that, on PRs touching library source / `.api` / `examples/taskflow`, diffs the change against the provenance-headed knowledge refs and comments which refs likely need updating. Ships ready; requires the `ANTHROPIC_API_KEY` secret (documented; cleanly skips without it).

**Architecture:** One `.github/workflows/knowledge-sync.yml` (path-filtered `pull_request` + `workflow_dispatch`, advisory/non-blocking, secret-guarded) + `docs/agent/knowledge-sync.md` setup doc. No source changes. Not locally runnable (LLM action) — verified by YAML validity + structure.

**Tech Stack:** GitHub Actions YAML, `anthropics/claude-code-action`.

---

## Task 1: `.github/workflows/knowledge-sync.yml`

**Files:** Create `.github/workflows/knowledge-sync.yml`

- [ ] **Step 1:** Write:
```yaml
name: Knowledge sync
on:
  pull_request:
    paths:
      - 'redux-kotlin*/**/src/**'
      - '**/api/*.api'
      - 'examples/taskflow/**'
  workflow_dispatch:
permissions:
  contents: read
  pull-requests: write
concurrency:
  cancel-in-progress: true
  group: knowledge-sync-${{ github.workflow }}-${{ github.head_ref || github.ref }}
jobs:
  sync:
    name: Knowledge drift review
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - name: Gate on API key presence
        id: gate
        run: echo "enabled=${{ secrets.ANTHROPIC_API_KEY != '' }}" >> "$GITHUB_OUTPUT"
      - name: Checkout
        if: steps.gate.outputs.enabled == 'true'
        uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - name: Claude knowledge-drift review
        if: steps.gate.outputs.enabled == 'true'
        continue-on-error: true
        uses: anthropics/claude-code-action@v1
        with:
          anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}
          prompt: |
            You are the redux-kotlin knowledge-sync reviewer. This PR changed library source, a committed
            `.api` dump, and/or `examples/taskflow` (the canonical reference app).

            The agent knowledge lives under `docs/agent/`:
            - `docs/agent/references/*.md` — T1 per-concern guides; each has a YAML `derives_from:` header
              listing the `path → Symbol` sources it paraphrases.
            - `AGENTS.md` — the T0 index (modules, commands, Rules C–H, layout).
            - `docs/agent/api-map.md` — module → `.api` map.

            Do this:
            1. Read the PR diff (changed files).
            2. For each knowledge doc, read its `derives_from` provenance; a doc is IN SCOPE if any changed
               file matches one of its `derives_from` paths (or it documents a changed module / Rule).
            3. For each in-scope doc, judge whether the change makes its prose, patterns, citations, or rule
               statements stale — focus on SEMANTIC drift (deterministic anchor/API checks run separately).
            4. Post ONE concise PR comment: a bullet per doc needing an update with the specific reason, or
               exactly "No knowledge drift detected." Do not modify files. Do not restate the rules.
            Keep it short and high-signal. Cite `path → Symbol` for any claim.
```
- [ ] **Step 2:** Validate: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/knowledge-sync.yml')); print('yaml ok')"`. If `actionlint` is installed, run it.
- [ ] **Step 3:** Sanity-check trigger globs resolve to real locations:
```bash
ls -d redux-kotlin*/src >/dev/null 2>&1 && echo "src glob ok"
ls */api/*.api >/dev/null 2>&1 && echo "api glob ok"
ls -d examples/taskflow >/dev/null 2>&1 && echo "taskflow ok"
```
Expected: all three echo. (Note: the workflow uses `redux-kotlin*/**/src/**` which covers `<module>/src/...`.)
- [ ] **Step 4:** Commit: `git add .github/workflows/knowledge-sync.yml && git commit -m "ci(agent): add knowledge-sync drift-review workflow"`

---

## Task 2: `docs/agent/knowledge-sync.md`

**Files:** Create `docs/agent/knowledge-sync.md`

- [ ] **Step 1:** Write:
```markdown
---
tier: T2
concern: knowledge-sync
derives_from: []
api_files: []
rules: []
assembles_into: []
last_verified: { commit: <short-sha>, date: 2026-06-03 }
---

# Knowledge-sync agent

An advisory GitHub Action (`.github/workflows/knowledge-sync.yml`) that catches **semantic** drift the
deterministic L4 checks (`scripts/check-agent-refs.sh`, `scripts/check-api-tripwire.sh`) cannot see:
when a code change makes a guide's prose or patterns subtly wrong while every cited path still resolves.

## What it does

On a PR touching library `*/src/**`, a committed `**/api/*.api`, or `examples/taskflow/**`, it runs Claude
to diff the change against the provenance-headed knowledge under `docs/agent/` and posts **one** PR comment
listing which references likely need updating (using each doc's `derives_from` header to target the review),
or "No knowledge drift detected." It is **advisory** — it never fails the build.

## Enabling it

1. Add a repository secret **`ANTHROPIC_API_KEY`** (Settings → Secrets and variables → Actions). Without it
   the workflow's single job **cleanly skips** (the `gate` step yields `enabled=false`).
2. **Pin the action.** Verify `anthropics/claude-code-action` inputs against its current README and pin to a
   release tag or commit SHA before relying on it (this workflow uses `@v1`).
3. (Optional) The workflow also supports `workflow_dispatch` for manual runs.

## Relationship to the deterministic checks

| Check | Catches | Blocking |
|---|---|---|
| `check-agent-refs.sh` (Phase 3) | broken `path → symbol` anchors, bad module/task names | yes |
| `check-api-tripwire.sh` (Phase 3) | any committed `.api` change | yes (bypass label `api-reviewed`) |
| knowledge-sync (this) | semantic / prose / pattern drift | no (advisory comment) |
```
Replace `<short-sha>` with `git rev-parse --short HEAD`.
- [ ] **Step 2:** If `scripts/check-agent-refs.sh` exists on this branch, run it — `docs/agent/knowledge-sync.md` must not break it (it adds no `path → symbol` anchors, so it should pass). Expected `ANCHOR CHECK OK`.
- [ ] **Step 3:** Commit: `git add docs/agent/knowledge-sync.md && git commit -m "docs(agent): document the knowledge-sync agent + required secret"`

---

## Task 3: Acceptance + PR

- [ ] **Step 1:** YAML valid; trigger globs resolve; secret-guard present (`grep -q "ANTHROPIC_API_KEY != ''" .github/workflows/knowledge-sync.yml`); `permissions` minimal; `bash scripts/check-agent-refs.sh` → `ANCHOR CHECK OK`.
- [ ] **Step 2:** No-collateral: `git diff --name-only l4-ci-phase3...HEAD` → only `.github/workflows/knowledge-sync.yml`, `docs/agent/knowledge-sync.md`, `docs/superpowers/`. No `.kt`/`website/`/`examples/`.
- [ ] **Step 3:** `git push -u origin knowledge-sync-phase4`
- [ ] **Step 4:** `gh pr create --base l4-ci-phase3 --head knowledge-sync-phase4` — title `ci(agent): knowledge-sync drift-review agent (Phase 4)`; body: what it does, advisory + secret-guarded (skips without `ANTHROPIC_API_KEY`), pin-the-action caveat, completes the L4 drift-control pair (deterministic + semantic), stacked merge-order note, link the spec.

---

## Self-review notes
- Spec coverage: workflow (T1), doc (T2), acceptance+PR (T3).
- Not locally runnable (LLM) — verification is YAML/structure/secret-guard, explicitly per spec.
- Secret-guard uses a `run`-step boolean output (`secrets` usable in `${{ }}`, not in job `if:`) — the robust pattern.
- Advisory: `continue-on-error: true`, no required status.
- Single-source: prompt points at `docs/agent/`; embeds no rule copies.
