# Phase 2: Claude skill

**Date:** 2026-06-03
**Phase:** 2 of the redux-kotlin AI integration strategy.
**Branch:** `claude-skill-phase2` (stacked on `agents-md-phase1`).
**Type:** Docs + a one-line `.gitignore` change. No source/build changes.

## Why

The premium Claude Code path: a project skill whose `SKILL.md` is a **rules card + decision routing** that sends Claude to the right T1 guide on demand (progressive disclosure). Single-source: the skill **links into** the canonical `docs/agent/references/` set authored in Phase 0 — it does not copy guide content. The skill *is* the Claude-facing progressive-disclosure layer over the same knowledge `AGENTS.md` indexes.

## Decisions (recommended, locked)

- **Location:** `.claude/skills/redux-kotlin/SKILL.md` — the conventional Claude Code project-skill path (auto-discovered). Requires un-ignoring it: `.gitignore` currently has `.claude/`; change to `.claude/*` + `!.claude/skills/` so `.claude/skills/` is tracked while `.claude/worktrees/` and other transient `.claude/` content stay ignored.
- **No duplicated `references/` dir.** `SKILL.md` routing links point up to the canonical `docs/agent/references/*.md` (relative `../../../docs/agent/references/...`), plus `AGENTS.md` (T0) and `docs/agent/api-map.md` (T2). This preserves single-source (one boundary: refs ↔ implementation).

## Deliverables

1. **`.gitignore`** — `.claude/` → `.claude/*` and `!.claude/skills/` (worktrees etc. remain ignored). Verify `git check-ignore .claude/worktrees/x` still ignored and `.claude/skills/redux-kotlin/SKILL.md` is NOT ignored.
2. **`.claude/skills/redux-kotlin/SKILL.md`** — frontmatter + body:
   - **Frontmatter:** `name: redux-kotlin`; `description:` a tight trigger statement (use when building/reviewing redux-kotlin KMP code — features, store setup, Compose binding, effects/sync, testing, platform shims, modularization).
   - **Rules card:** Rules C–H one-liners (faithful to `ARCHITECTURE.md` §17; no fabricated A/B) — the always-apply constraints. Keep terse; point to `AGENTS.md` / `ARCHITECTURE.md` for full text.
   - **Decision routing:** a table mapping intent → guide, e.g. "Adding/editing a feature → `docs/agent/references/feature-slice.md`"; the other six concerns (store-setup, compose-binding, effects-sync, testing, platform-shims, modularization) routed to their planned `docs/agent/references/*.md` paths, each marked *(planned — see README)* until 0-rest lands.
   - **Pointers:** T0 index → `AGENTS.md`; public API → `docs/agent/api-map.md`; deep dive → `examples/taskflow/ARCHITECTURE.md`; ref index → `docs/agent/references/README.md`.

## Constraints

- `SKILL.md` is loaded into context when the skill activates — keep it tight (rules card + routing, not a manual). Depth lives in the linked guides.
- Single-source: do NOT restate guide content in `SKILL.md`; link to it.
- Routing links use repo-relative paths from the skill dir (`../../../docs/agent/references/...`); every linked path that already exists must resolve (planned guides are explicitly marked, not broken-linked as if present).

## Acceptance gate

- `.gitignore` behaves: `.claude/worktrees/<x>` still ignored; `.claude/skills/redux-kotlin/SKILL.md` tracked (`git check-ignore` + `git status` confirm).
- Every routing/pointer link in `SKILL.md` that points to an **existing** file resolves (feature-slice.md, README.md, AGENTS.md, api-map.md, ARCHITECTURE.md). Planned-guide paths are present in the table but clearly labelled *(planned)*; they are not asserted to exist.
- Rules C–H faithful to ARCHITECTURE §17; no A/B fabrication.
- No `.kt`/`website/`/`examples/` changes.

## Out of scope

- L4 CI (Phase 3), sync agent (Phase 4), authoring the six planned guides (0-rest).

## Next

Phase 3 (L4 deterministic CI) anchor-checks the refs the skill routes to + AGENTS.md/api-map.
