---
tier: T1
concern: <kebab-concern-name>
derives_from:
  # each entry is `<repo-relative-path> → <Symbol>[, <Symbol>]`
  - <path> → <Symbol>
api_files:
  # committed library .api dumps backing the APIs this guide uses (taskflow itself has none)
  - <module>/api/<module>.klib.api
rules: []          # subset of A–H this guide enforces
assembles_into: [AGENTS.md, claude-skill]
# last_verified.commit: the commit the cited `derives_from` sources were verified against
# (use the repo base commit if this doc derives from nothing).
last_verified: { commit: <short-sha>, date: <YYYY-MM-DD> }
---

# <Title>

> One-line statement of what this guide answers.

## <Section>

Prose explains the *why* and the *rule*. Every source reference uses the anchor form
`path → Symbol` wrapped in backticks — repo-relative path, a literal " → ", then the symbol(s).

## Anchor rules (enforced by `scripts/check-agent-refs.sh`)

- A source citation is `` `path → Symbol` `` where **path must contain `/`** — bare names are not
  validated (they're treated as convention/example placeholders).
- The checker also verifies cited `:redux-kotlin*` **module names** exist in `settings.gradle.kts`
  and that cited `./gradlew <task>` names are in its allowlist.
- Lines containing `(planned` are skipped (so you can point at not-yet-written guides).

## See also

- Cross-links to sibling guides via [README](./README.md).
