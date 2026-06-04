# Single-source assembler for agent knowledge

**Date:** 2026-06-03
**Sub-project:** Post-review #1 (resolves strategy open-question Q6 + the §5 L1 "author once, assemble" principle).
**Branch:** `single-source-assembler` (stacked on `knowledge-sync-phase4`).
**Type:** Scripts + CI + doc retrofit. No library source changes.

## Why

The review found the strategy's central single-source discipline was dropped: the **Rules card (C–H)** is hand-copied into both `AGENTS.md` and `SKILL.md`, and the **module map** lives in `AGENTS.md`. They have **already drifted** (AGENTS.md's Rule C omits the `key(...)` requirement that SKILL.md and ARCHITECTURE §17 state; wordings differ). The deterministic anchor checker validates paths/symbols, not prose, so this drift is unguarded. Fix: author each shared block **once** as a fragment and *assemble* it into the consuming surfaces, with a CI check that fails on drift.

## Decisions (recommended, locked)

- **Mechanism: marker-injection into checked-in files.** `AGENTS.md`/`SKILL.md` stay human-readable and directly committed (tools read them as-is); only the shared regions are generated, delimited by HTML-comment markers. Preferred over full-file generation (keeps files editable/reviewable) and over include-directives (agents/tools don't process includes).
- **Fragments** in `docs/agent/_fragments/` (leading `_` groups them; the anchor checker already only scans `references/*.md`+`AGENTS.md`+`api-map.md`, not `_fragments/`).
- **Scope (focused):** single-source the two genuinely-duplicated, always-loaded surfaces — **Rules C–H** (→ `AGENTS.md` + `SKILL.md`) and the **module map** (→ `AGENTS.md`). `api-map.md` stays the `.api` index (T2, on-demand) and is documented as a future fragment-consumer, not retrofitted now (its table format + non-core rows make injection low-value). `CLAUDE.md` is the maintainer doc — untouched.
- **Canonical content:** the Rules fragment is the corrected, faithful-to-ARCHITECTURE-§17 version (Rule C **includes** `key(...)`), ending the existing drift.
- **CI: a new workflow** `.github/workflows/assemble-check.yml` (separate from `agent-knowledge.yml` to avoid colliding with the in-flight #7 fix on that file). Runs the assembler in `--check` mode.

## Deliverables

1. **`docs/agent/_fragments/rules.md`** — the canonical Rules C–H bullet list (faithful to `examples/taskflow/ARCHITECTURE.md` §17; C includes `key(...)`). Just the bullets (no header) so it drops into each surface's existing section.
2. **`docs/agent/_fragments/modules.md`** — the canonical 9-core-module map (one `- \`module\` — use for X` line each).
3. **`scripts/assemble-agent-knowledge.sh`** — injects each fragment into marker-delimited regions of its target(s):
   - `rules` → `AGENTS.md` + `.claude/skills/redux-kotlin/SKILL.md`
   - `modules` → `AGENTS.md`
   - Default mode rewrites the regions in place; `--check` mode regenerates to a temp copy and `diff`s — exit non-zero (listing the stale file) if any target's region differs from its fragment. Idempotent (running twice = no change).
4. **Marker retrofit** of `AGENTS.md` (Design-rules region + Module-map region) and `SKILL.md` (Always-apply-rules region) with `<!-- assemble:rules:start -->`…`<!-- assemble:rules:end -->` / `:modules:` markers; the region bodies become the fragment content (removing the hand-copied duplicates).
5. **`.github/workflows/assemble-check.yml`** — `pull_request: [master]`; `permissions: contents: read`; runs `bash scripts/assemble-agent-knowledge.sh --check`.
6. Brief note in `docs/agent/references/_template.md` (or a one-line README mention): shared blocks (rules, module map) are single-sourced in `docs/agent/_fragments/`; edit there, then run `scripts/assemble-agent-knowledge.sh`.

## Constraints / invariants

- After assembly, `scripts/check-agent-refs.sh` must still pass (`ANCHOR CHECK OK`) — the injected rules/module text contains no `path → symbol` anchors that would newly fail.
- `AGENTS.md` stays token-tight (the change removes duplication, doesn't add prose).
- The assembler must be deterministic + idempotent; `--check` is clean immediately after a default run.
- Markers must not break markdown rendering (HTML comments are invisible).
- No change to `.github/workflows/agent-knowledge.yml` (avoid the #7-fix collision).

## Acceptance gate (testable locally)

- `bash scripts/assemble-agent-knowledge.sh` then `git diff` — regions match fragments; running again is a no-op.
- `bash scripts/assemble-agent-knowledge.sh --check` exits **0** on the committed state.
- **Negative test:** edit a fragment (or a generated region) so they diverge → `--check` exits non-zero naming the stale file; revert.
- `bash scripts/check-agent-refs.sh` → `ANCHOR CHECK OK`.
- Rules C–H in the assembled `AGENTS.md` now include `key(...)` (drift fixed); `AGENTS.md` and `SKILL.md` rules are byte-identical in the generated region.
- YAML valid; no `.kt`/`website/`/`examples/`/`agent-knowledge.yml` changes.

## Out of scope

- Retrofitting `api-map.md` to consume `modules.md` (documented as future).
- Any change to `CLAUDE.md`.
- The six planned T1 guides (0-rest).

## Resolves

Strategy open-question **Q6** (AGENTS.md ↔ CLAUDE.md / single-source): recorded resolution — `CLAUDE.md` stays the maintainer doc; agent surfaces are single-sourced from `docs/agent/_fragments/` and assembled. The §5 L1 "author once, assemble; do not maintain parallel copies" discipline is now implemented for the shared blocks.
