---
tier: T2
concern: knowledge-sync
derives_from: []
api_files: []
rules: []
assembles_into: []
last_verified: { commit: 06214a9, date: 2026-06-03 }
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
