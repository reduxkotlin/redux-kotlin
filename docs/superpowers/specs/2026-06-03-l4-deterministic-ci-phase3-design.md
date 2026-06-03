# Phase 3: L4 deterministic CI (anchor check + API-change tripwire)

**Date:** 2026-06-03
**Phase:** 3 of the redux-kotlin AI integration strategy.
**Branch:** `l4-ci-phase3` (stacked on `claude-skill-phase2`).
**Type:** CI + scripts. Adds shell scripts under `scripts/` (detekt-excluded) + one GitHub Actions workflow. No library source changes.

## Why

L4 keeps the knowledge refs honest *deterministically* (no LLM), closing the cheap half of the drift surface before the Phase-4 semantic agent. Two checks:
1. **Anchor check** — refs cite real things: every `path → symbol` anchor resolves (path exists + symbol present), every cited module name exists in `settings.gradle.kts`, every cited gradle task is real. This is the automated form of the manual sweep dry-run from Phases 0–1.
2. **API-change tripwire** — any committed `*.api` change in a PR flags "public API changed — knowledge review required" and names the affected modules, so the API-map / refs get re-checked.

## Decisions (recommended, locked)

- **Scripts in `scripts/`** (`check-agent-refs.sh`, `check-api-tripwire.sh`) — bash, runnable locally and in CI; `scripts/` is detekt-excluded so no lint friction. Deterministic, fast (no gradle build needed for the anchor check).
- **Command verification** is against a **static allowlist** of real root gradle tasks (`build`, `detektAll`, `apiCheck`, `apiDump`, `allTests`, `jvmTest`) rather than invoking gradle — keeps the check fast and hermetic. (Live `./gradlew tasks` verification is a documented stretch, not done here.)
- **One workflow** `.github/workflows/agent-knowledge.yml`, `on: pull_request: branches: [master]` (matches `pr.yml`), two jobs: `anchor-check` (blocking) and `api-tripwire` (blocking when `*.api` changed unless the PR carries the bypass label `api-reviewed`).

## Deliverables

1. **`scripts/check-agent-refs.sh`** — scans `docs/agent/references/*.md`, `AGENTS.md`, `docs/agent/api-map.md`. For each file:
   - Extract backtick anchors `` `path → Sym[, Sym]` `` and YAML `derives_from:` / `api_files:` entries.
   - Verify: each `path` exists; each `Sym` appears in `path` (grep -r); each `api_files` path exists.
   - Verify cited module tokens matching `:redux-kotlin[-a-z]*` exist in `settings.gradle.kts`.
   - Verify cited `./gradlew <task>` `<task>` ∈ the static allowlist.
   - Exit 0 if all resolve; non-zero listing each failure. Skip anchors explicitly marked `*(planned)*`.
2. **`scripts/check-api-tripwire.sh`** — args: base ref (default `origin/master`). Lists changed `*.api` files vs base; if any, prints the affected module dirs + remediation ("update `docs/agent/api-map.md`; add label `api-reviewed` to ack") and exits non-zero unless `API_REVIEW_OK=1` (the CI job sets this when the bypass label is present).
3. **`.github/workflows/agent-knowledge.yml`** — `pull_request: [master]`; `anchor-check` job runs script 1; `api-tripwire` job computes the PR base, checks for label `api-reviewed` (via `github` context), sets `API_REVIEW_OK` accordingly, runs script 2.

## Acceptance gate (testable locally)

- `bash scripts/check-agent-refs.sh` exits **0** against the committed refs (feature-slice.md, AGENTS.md, api-map.md). 
- A **negative test**: temporarily appending a bogus anchor `` `docs/agent/nope.md → Ghost` `` makes it exit non-zero naming that anchor; revert.
- `bash scripts/check-api-tripwire.sh origin/master` exits **0** on this branch (Phases 0–3 are docs/CI only — no `.api` changed); a negative test (touch a `.api`) trips it; revert.
- `actionlint` clean if available, else YAML parses; workflow `on`/jobs/steps well-formed.
- No library `.kt`/`website/`/`examples/` changes.

## Out of scope

- The Phase-4 LLM knowledge-sync agent.
- Live gradle-task existence verification (static allowlist instead).
- Wiring into `pr.yml` (standalone workflow; same trigger).

## Next

Phase 4 (knowledge-sync agent) adds the semantic-drift catch on top of these deterministic checks.
