# Phase 1: `AGENTS.md` (T0) + API-map index

**Date:** 2026-06-03
**Phase:** 1 of the redux-kotlin AI integration strategy.
**Branch:** `agents-md-phase1` (stacked on `feature-slice-pilot`).
**Type:** Documentation assembly. No source/build changes.

## Why

Phase 0 established the single-source reference set under `docs/agent/references/`. Phase 1 produces the **T0 layer** — the always-in-context, token-tight index every agent/tool (Cursor, Copilot, Codex, Claude) reads first — by **assembling** it from existing canon, plus an **API-map index** so an agent reads the committed public-API surface (`.api`) instead of source. Single-source discipline: AGENTS.md *points into* `docs/agent/references/` and `CLAUDE.md`; it does not duplicate their content.

Decisions (recommended, locked for unattended execution):
- **AGENTS.md** lives at repo root (tool-agnostic convention).
- **`CLAUDE.md` stays as-is** (maintainer/build-gate doc). AGENTS.md cross-links it for build/lint/publish detail rather than merging (avoids disrupting a working doc; resolves strategy open-question 6 minimally).
- **API map** at `docs/agent/api-map.md` — table of the 9 published modules → `.api` dump path + one-line surface summary.

## Deliverables

1. **`AGENTS.md`** (repo root) — T0 index, token-tight (~120–180 lines). Sections:
   - **What redux-kotlin is** — 1 paragraph.
   - **Module map** — which module for which job (the 9 published modules + the `examples/` samples), derived from `CLAUDE.md`.
   - **Build / test / lint commands** — the gate one-liners (`./gradlew build`, `detektAll`, `apiCheck`, target-scoped test), pointer to `CLAUDE.md` for detail.
   - **Design rules** — one line each. ARCHITECTURE.md §17 names **Rules C–H** only (the strategy's "A–H" is loose shorthand; there are no named Rules A/B). Use the canonical C–H statements verbatim — do **not** fabricate A/B.
   - **Where things live** — the package-by-feature layout (`core/ infra/ feature/ app/ ui/`) as the recommended app organization (§5.1), citing taskflow.
   - **Deeper knowledge** — pointers into T1 (`docs/agent/references/` — list the 7 guides + status) and T2 (`examples/taskflow/ARCHITECTURE.md`, `docs/agent/api-map.md`).
2. **`docs/agent/api-map.md`** — provenance-headed (same YAML convention as the ref set, `tier: T2`); a table: module → `api/<module>.klib.api` → one-line "what's in it". Covers **every published module that has a committed `.api` dump** (enumerate via `ls */api/*.klib.api` — `settings.gradle.kts` lists ~20 modules incl. routing/bundle/bom/devtools, more than CLAUDE.md's "core 9"; map exactly those with a dump). Note `examples/*` are `convention.control` (no `.api`).

## Constraints

- **Anchor convention** (inherited from Phase 0): file references use backtick-wrapped `` `path → Symbol` `` or plain `` `path` `` for whole-file/dir; no line numbers. Every cited path must exist (Phase-3 anchor checker will enforce; Phase 1 self-checks).
- AGENTS.md must be **token-tight** — it is always in context. Prose minimal; it is an index, not a manual. Defer depth to T1/T2 via pointers.
- Rules A–H wording must match `ARCHITECTURE.md` (don't invent new phrasings).
- No duplication of `CLAUDE.md` content — reference it.

## Acceptance gate

Docs-only. Gate = **reference integrity**: every path referenced in `AGENTS.md` and `api-map.md` exists (the 9 `.api` files, `docs/agent/references/*`, `examples/taskflow/ARCHITECTURE.md`, `CLAUDE.md`); the 9 module names match `settings.gradle.kts`; cited gradle tasks are real. Implemented as a grep/test sweep (extends the Phase-0 sweep; a dry-run of the Phase-3 checker). Plus: `AGENTS.md` ≤ ~180 lines (token-tight); no `.kt`/`website/`/`examples/` changes.

## Out of scope

- The Claude skill (Phase 2), L4 CI (Phase 3), sync agent (Phase 4).
- Authoring the remaining six T1 guides (Phase 0-rest) — AGENTS.md lists them with status, pointing at the canonical location as they land.
- Any merge/rewrite of `CLAUDE.md`.

## Next

Phase 2 (Claude skill) consumes this T0 + the ref set.
