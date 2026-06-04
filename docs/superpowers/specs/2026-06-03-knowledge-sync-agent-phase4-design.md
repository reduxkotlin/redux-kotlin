# Phase 4: knowledge-sync agent (semantic drift)

**Date:** 2026-06-03
**Phase:** 4 (final) of the redux-kotlin AI integration strategy.
**Branch:** `knowledge-sync-phase4` (stacked on `l4-ci-phase3`).
**Type:** CI + docs. Adds one GitHub Actions workflow + a setup doc. No source changes.

## Why

Phase 3 catches *structural* drift deterministically (broken anchors, API changes). It cannot see *semantic* drift — when a code change makes a guide's prose/pattern subtly wrong while every cited path still resolves. Phase 4 adds the LLM catch: on a PR that touches library source, `.api` dumps, or the canonical `examples/taskflow`, an agent diffs the change against the knowledge refs (which carry `derives_from` provenance) and comments which refs likely need updating. Advisory, not blocking — it complements, doesn't gate.

## Decisions (recommended, locked)

- **Mechanism:** the official Claude GitHub Action (`anthropics/claude-code-action`) in headless/prompt mode, given a focused prompt + read access to the diff and `docs/agent/`. Avoids bespoke API plumbing.
- **Trigger:** `pull_request` touching `redux-kotlin*/**/src/**`, `**/api/*.api`, or `examples/taskflow/**` (path-filtered). Optionally a weekly `schedule` as a safety net.
- **Advisory:** the job posts a PR comment; it does **not** fail the build (`continue-on-error` / no required status). Drift is surfaced for a human, per the strategy.
- **Secret:** requires repo secret `ANTHROPIC_API_KEY`. This **cannot be set unattended** — the workflow ships ready, and `docs/agent/knowledge-sync.md` documents enabling it. Until the secret exists the job no-ops/skips cleanly (guard on `secrets.ANTHROPIC_API_KEY != ''`).
- **Provenance-targeted:** the prompt instructs the agent to use each ref's `derives_from` header to decide relevance — targeted, not whole-repo guesswork (matches the L4 "provenance discipline").

## Deliverables

1. **`.github/workflows/knowledge-sync.yml`** — `pull_request` (path-filtered) + `workflow_dispatch`; `permissions: contents: read, pull-requests: write`; a single job guarded by `if: ${{ secrets.ANTHROPIC_API_KEY != '' }}` that:
   - checks out (fetch-depth 0),
   - runs `anthropics/claude-code-action` with `anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}` and a `prompt` that: reads the PR diff; for each changed library/source/api/taskflow path, consults `docs/agent/references/*.md` + `AGENTS.md` + `docs/agent/api-map.md` provenance (`derives_from`) to find refs that derive from the changed files; posts ONE PR comment listing each ref that likely needs updating and why (or "no knowledge drift detected"). Read-only to the repo besides the comment.
   - `continue-on-error: true` (advisory).
2. **`docs/agent/knowledge-sync.md`** — provenance-headed (`tier: T2`, concern `knowledge-sync`); explains: what the agent does, the required `ANTHROPIC_API_KEY` secret + how to add it, the trigger paths, that it's advisory (complements the deterministic Phase-3 checks), and how to pin/verify the action version before relying on it.

## Constraints

- Cannot run the LLM action locally; verification is limited to **YAML validity, trigger-path correctness, the secret-guard, and a sound prompt**. The doc must flag "verify/pin `anthropics/claude-code-action` to a release before enabling."
- Must not block CI (advisory). Must not run (cleanly skip) when the secret is absent.
- Single-source: the prompt points the agent at the canonical `docs/agent/` knowledge; it does not embed copies of the rules.

## Acceptance gate

- `knowledge-sync.yml` parses (`python3 -c "import yaml,…"`); `actionlint` clean if available.
- Trigger `paths:` globs match real locations (`redux-kotlin*/.../src`, `**/api/*.api`, `examples/taskflow/**`).
- Secret-guard present (`if: secrets.ANTHROPIC_API_KEY != ''`).
- `permissions` minimal (`contents: read`, `pull-requests: write`).
- `docs/agent/knowledge-sync.md` exists, names the secret, and is itself anchor-clean (passes `scripts/check-agent-refs.sh` if it adds anchors).
- No `.kt`/`website/`/`examples/` source changes.

## Out of scope

- Setting the secret (manual, documented).
- The parked "snippet compile-check" stretch from the strategy.
- Authoring the six remaining T1 guides (Phase 0-rest).

## Next (post-initiative)

Phase 0-rest (the six remaining T1 guides), then the strategy's success-metric baselining. The deterministic (Phase 3) + semantic (Phase 4) drift controls now both exist.
