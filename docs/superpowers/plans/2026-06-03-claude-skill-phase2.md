# Phase 2 — Claude Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Add the `redux-kotlin` Claude Code project skill — a rules-card + decision-routing `SKILL.md` that links into the canonical `docs/agent/references/` set (single-source).

**Architecture:** One `.gitignore` change to make `.claude/skills/` trackable, plus `SKILL.md`. No content duplication; routing links point to the Phase-0/1 docs. Gate = gitignore behavior + link resolution.

**Tech Stack:** Markdown + `.gitignore`.

---

## Task 1: Make `.claude/skills/` trackable

**Files:** Modify `.gitignore`

- [ ] **Step 1:** In `.gitignore`, replace the line `.claude/` with two lines:
```
.claude/*
!.claude/skills/
```
- [ ] **Step 2:** Verify behavior:
```bash
git check-ignore .claude/worktrees/foo >/dev/null && echo "worktrees still ignored ✓"
git check-ignore .claude/skills/redux-kotlin/SKILL.md && echo "SKILL still ignored ✗" || echo "skills trackable ✓"
```
Expected: `worktrees still ignored ✓` and `skills trackable ✓`.
- [ ] **Step 3:** Commit: `git commit -am "chore: track .claude/skills (keep worktrees ignored)"`

---

## Task 2: `SKILL.md`

**Files:** Create `.claude/skills/redux-kotlin/SKILL.md`

Read first: `AGENTS.md`, `examples/taskflow/ARCHITECTURE.md` §17 (Rules C–H), `docs/agent/references/README.md`.

- [ ] **Step 1:** Create `.claude/skills/redux-kotlin/SKILL.md`:

````markdown
---
name: redux-kotlin
description: Use when building or reviewing redux-kotlin (Kotlin Multiplatform Redux) code — adding/editing a feature slice, wiring the store, Compose state binding, effects/sync middleware, testing, the platform expect/actual shims, or modularization. Enforces the project's render-isolation / off-main-effects / mint-at-edge rules.
---

# redux-kotlin

Kotlin Multiplatform port of the Redux contract: a minimal core (`redux-kotlin`) plus opt-in
companion modules on one `Store<S>` contract. Recommended app organization is **package-by-feature**
(`feature/<name>/` slice + shared `core` · `infra` · `app` · `ui`); the canonical example is `examples/taskflow`.

## Always-apply rules (full text → `../../../examples/taskflow/ARCHITECTURE.md` §17)

- **Rule C — Render isolation.** No composable reads a model wholesale; bind the narrowest slice via `selectorState`/`fieldStateOf`, wrapped in `key(...)`. Derivation lives in pure functions/reducers.
- **Rule D — Identity split.** A profile edit fans to the root account directory, per-account `CollaboratorsModel`, and `SessionModel` — never duplicate identity inconsistently.
- **Rule E — Off-main effects.** Effects originate only in middleware and run off-main; dispatch marshals back to main via `NotificationContext`. Per-feature handlers compose into one `effectsMiddleware`.
- **Rule F — Delta-only status.** Emit status only on a real change.
- **Rule G — Mint at the edge.** Ids/timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — Single inset point.** Window insets applied once at the shell root.

## Decision routing

| If you are… | Read |
|---|---|
| Adding or editing a **feature** | [`feature-slice.md`](../../../docs/agent/references/feature-slice.md) |
| Setting up the **store / topology** | `../../../docs/agent/references/store-setup.md` *(planned — see README)* |
| **Compose** state binding (Rule C) | `../../../docs/agent/references/compose-binding.md` *(planned)* |
| **Effects + sync** (Rule E) | `../../../docs/agent/references/effects-sync.md` *(planned)* |
| **Testing** + the verify loop | `../../../docs/agent/references/testing.md` *(planned)* |
| The 6 **platform shims** | `../../../docs/agent/references/platform-shims.md` *(planned)* |
| **Modularization** | `../../../docs/agent/references/modularization.md` *(planned)* |

## Pointers

- T0 index (modules, commands, layout): [`AGENTS.md`](../../../AGENTS.md)
- Public API surface per module: [`api-map.md`](../../../docs/agent/api-map.md)
- Architecture deep-dive: [`ARCHITECTURE.md`](../../../examples/taskflow/ARCHITECTURE.md)
- Reference index: [`references/README.md`](../../../docs/agent/references/README.md)

Build/lint gate (never `--no-verify`; `explicitApi()` needs KDoc on every public decl): `./gradlew build`, `./gradlew detektAll`, `./gradlew apiCheck`. Detail → `../../../CLAUDE.md`.
````

- [ ] **Step 2:** Verify the existing-file links resolve (planned ones are labelled, skip them):
```bash
cd .claude/skills/redux-kotlin
for p in ../../../docs/agent/references/feature-slice.md ../../../docs/agent/references/README.md ../../../AGENTS.md ../../../docs/agent/api-map.md ../../../examples/taskflow/ARCHITECTURE.md ../../../CLAUDE.md; do test -f "$p" && echo "OK $p" || echo "MISS $p"; done
cd - >/dev/null
```
Expected: all `OK`, no `MISS`.
- [ ] **Step 3:** Confirm Rules C–H match ARCHITECTURE §17 wording (no A/B). 
- [ ] **Step 4:** Commit: `git add .claude/skills/redux-kotlin/SKILL.md && git commit -m "feat(agent): add redux-kotlin Claude skill (rules + routing)"`

---

## Task 3: Acceptance gate

**Files:** none.

- [ ] **Step 1:** Re-run Task 1 Step 2 (gitignore) + Task 2 Step 2 (links) — all green.
- [ ] **Step 2:** `git status --porcelain` shows `.claude/skills/redux-kotlin/SKILL.md` tracked; `git diff --name-only agents-md-phase1...HEAD` shows only `.gitignore`, `.claude/skills/...`, `docs/superpowers/...`. No `.kt`/`website/`/`examples/`.

---

## Task 4: PR

- [ ] **Step 1:** `git push -u origin claude-skill-phase2`
- [ ] **Step 2:** `gh pr create --base agents-md-phase1 --head claude-skill-phase2` — title `feat(agent): redux-kotlin Claude skill (Phase 2)`; body: rules-card + routing, single-source (links into refs, no duplication), the `.gitignore` rationale, gate result, stacked-on-#314 merge-order note, link the spec.

---

## Self-review notes
- Spec coverage: gitignore (T1), SKILL.md (T2), gate (T3), PR (T4).
- Single-source: routing links point to canonical docs; no guide content copied into SKILL.md.
- Planned guides are labelled, not asserted present — gate only checks existing files.
- gitignore change keeps worktrees ignored (verified in T1 Step 2).
