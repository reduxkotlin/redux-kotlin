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
last_verified: { commit: <short-sha>, date: <YYYY-MM-DD> }
---

# <Title>

> One-line statement of what this guide answers.

## <Section>

Prose explains the *why* and the *rule*. Every source reference uses the anchor form
`path → Symbol` wrapped in backticks — repo-relative path, a literal " → ", then the symbol(s).

## See also

- Cross-links to sibling guides via [README](./README.md).
