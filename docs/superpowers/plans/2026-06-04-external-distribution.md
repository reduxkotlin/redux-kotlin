# External Distribution of Agent Knowledge — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the redux-kotlin agent knowledge for external adopters: a reduxkotlin.org "Building with AI agents" page that doubles as the copy-source for a repo-droppable external `AGENTS.md`, single-sourced from the existing `_fragments` so it can't drift.

**Architecture:** A committed `docs/agent/AGENTS-external.md` (external-pointer AGENTS template) whose `rules`/`modules` regions are refreshed by the existing `scripts/assemble-agent-knowledge.sh` from the same fragments as the in-repo `AGENTS.md`. The assembler additionally regenerates a fenced copy-block inside a plain-markdown Docusaurus page (`website/docs/ai-agents/BuildingWithAI.md`) — no MDX, no raw-loader, no new npm deps. CI guards: `assemble-check` (drift), a reference-link existence check (renamed guides), and `yarn build` with `onBrokenLinks: throw`.

**Tech Stack:** Bash (assembler + checks), Docusaurus 3.10.1 (TS config, `website/`), Markdown.

**Reference spec:** `docs/superpowers/specs/2026-06-04-external-distribution-design.md`

---

## Conventions / facts for this plan

- Assembler model: `scripts/assemble-agent-knowledge.sh` injects a fragment file's content between `<!-- assemble:<marker>:start -->` / `<!-- assemble:<marker>:end -->` in each target listed in its `MAP=( "target|marker|fragmentfile" … )` array. `--check` diffs the rendered result against the committed file and fails on mismatch. Targets are hand-written files that already contain the markers (they are both source and output).
- Fragments: `docs/agent/_fragments/rules.md`, `docs/agent/_fragments/modules.md`.
- Library coords: `group=org.reduxkotlin`; latest **published** release tag `0.6.1` (repo `version=0.6.2-SNAPSHOT`). Use `0.6.1` for Maven coordinates in the external template.
- The new reference guides post-date tag `0.6.1`, so GitHub links to them must target `master` (they 404 at `0.6.1`). Reference-guide links → `…/blob/master/docs/agent/references/<guide>.md`.
- Docusaurus 3.10.1: docs at `website/docs/`, `routeBasePath: '/'`, `onBrokenLinks: 'throw'`, `onBrokenMarkdownLinks: 'throw'`. Config files are `website/sidebars.ts` + `website/docusaurus.config.ts`. Files/dirs under `docs/` starting with `_` are NOT routed. Build: `cd website && yarn install && yarn build`. Node 22.x.
- Markdown docs are detekt-excluded; the bash scripts live under `scripts/` which detekt also excludes. The gates here are assemble-check / link-check / yarn build, not detekt.

---

## File Structure

- **Create** `docs/agent/AGENTS-external.md` — external AGENTS template: external boilerplate + pointers (GitHub reference URLs on `master`, Maven `0.6.1`, reduxkotlin.org) with `<!-- assemble:rules -->` and `<!-- assemble:modules -->` marker regions. Committed; assembler refreshes the regions.
- **Modify** `scripts/assemble-agent-knowledge.sh` — (a) add two `MAP` entries so `AGENTS-external.md`'s rules/modules regions assemble from the fragments; (b) add a dedicated step that regenerates the fenced copy-block in the Docusaurus page from `AGENTS-external.md`; (c) extend `--check` to cover both.
- **Create** `website/docs/ai-agents/BuildingWithAI.md` — the page (prose + a sentinel-delimited fenced block the assembler fills).
- **Modify** `website/sidebars.ts` — add an "AI Agents" category.
- **Modify** `website/docusaurus.config.ts` — add a navbar link.
- **Create** `scripts/check-site-agent-links.sh` — assert every GitHub reference-guide path linked from the page exists in the repo.
- **Modify** `.github/workflows/agent-knowledge.yml` — run the new link check alongside the existing anchor/assemble checks.

---

## Task 1: External `AGENTS-external.md` template + assembler rules/modules injection

**Files:**
- Create: `docs/agent/AGENTS-external.md`
- Modify: `scripts/assemble-agent-knowledge.sh` (MAP array)

- [ ] **Step 1: Create the external template (with marker regions + external pointers)**

Create `docs/agent/AGENTS-external.md`. The `rules`/`modules` regions are placeholders the assembler will fill; everything else is the external boilerplate:

```markdown
# AGENTS.md — redux-kotlin

Token-tight index for AI agents building an app **with** redux-kotlin
(Kotlin Multiplatform Redux). Drop this file at your repo root so any agent loads
it. It points to depth on github.com; it does not inline it.

## What redux-kotlin is

A Kotlin Multiplatform port of the Redux contract: a minimal core (`redux-kotlin`)
holding the `Store<S>` contract, plus opt-in companion modules (thread-safety,
concurrency, granular subscriptions, multi-store registries, a heterogeneous model
bag, Compose bindings) on that same contract. Take the core, add only what you need.

## Dependencies (Maven Central, group `org.reduxkotlin`)

```kotlin
// latest published release
implementation("org.reduxkotlin:redux-kotlin:0.6.1")
// add companions as needed, e.g.:
// implementation("org.reduxkotlin:redux-kotlin-compose:0.6.1")
```

## Module map

<!-- assemble:modules:start -->
<!-- assemble:modules:end -->

Full module → public-API index:
https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/api-map.md

## Design rules

Faithful one-liners from the canonical sample app (`examples/taskflow`):

<!-- assemble:rules:start -->
<!-- assemble:rules:end -->

Full architecture + rules:
https://github.com/reduxkotlin/redux-kotlin/blob/master/examples/taskflow/ARCHITECTURE.md

## Recommended app layout

Package-by-feature: a `feature/<name>/` slice (model, actions, reducer, effects,
screen, selectors, tests) plus shared `core` (domain kernel — no Compose/IO),
`infra` (platform shims, db, data/sync), `app` (composition root: store factories,
nav), and `ui` (theme, locals, widgets).

## Per-concern guides (read the one that matches your task)

- Add a feature slice — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/feature-slice.md
- Store setup & topology — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/store-setup.md
- Compose binding (render isolation) — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/compose-binding.md
- Effects + sync — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/effects-sync.md
- Testing & the verify loop — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/testing.md
- Platform shims (expect/actual) — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/platform-shims.md
- Modularization (which module when) — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/modularization.md
- DevTools debugging loop — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/devtools.md

## Verify loop

After writing code, run (Gradle): build (`./gradlew build`), lint
(`./gradlew detektAll`), and — if you publish a library module — `./gradlew apiCheck`.
`explicitApi()` projects need a KDoc on every public declaration.
```

> Note: the inner ```` ```kotlin ```` block above is part of the template content; keep it. The `<!-- assemble:* -->` marker pairs must be on their own lines and initially empty between start/end (the assembler fills them).

- [ ] **Step 2: Add MAP entries so the assembler fills its regions**

In `scripts/assemble-agent-knowledge.sh`, extend the `MAP` array to include the new target's two markers:

```bash
MAP=(
  "AGENTS.md|rules|$FRAG/rules.md"
  ".claude/skills/redux-kotlin/SKILL.md|rules|$FRAG/rules.md"
  "AGENTS.md|modules|$FRAG/modules.md"
  "docs/agent/AGENTS-external.md|rules|$FRAG/rules.md"
  "docs/agent/AGENTS-external.md|modules|$FRAG/modules.md"
)
```

- [ ] **Step 3: Run the assembler and verify it fills the template**

Run: `bash scripts/assemble-agent-knowledge.sh`
Expected: prints `ASSEMBLED`. `git diff docs/agent/AGENTS-external.md` shows the rules + modules one-liners now injected between the markers (identical text to what's in `AGENTS.md`).

- [ ] **Step 4: Verify --check passes**

Run: `bash scripts/assemble-agent-knowledge.sh --check`
Expected: `ASSEMBLE CHECK OK`. (Run the assembler first in Step 3 so the committed file is in sync.)

- [ ] **Step 5: Commit**

```bash
git add docs/agent/AGENTS-external.md scripts/assemble-agent-knowledge.sh
git commit -m "feat(agent): external AGENTS.md template, assembled from shared fragments"
```

---

## Task 2: The Docusaurus page + sidebar + navbar (with a placeholder copy-block)

**Files:**
- Create: `website/docs/ai-agents/BuildingWithAI.md`
- Modify: `website/sidebars.ts`
- Modify: `website/docusaurus.config.ts`

- [ ] **Step 1: Create the page with prose + a sentinel-delimited fenced block**

Create `website/docs/ai-agents/BuildingWithAI.md`. The fenced block between the sentinels is a placeholder the assembler will fill in Task 3; for now put a single line so the page builds:

````markdown
---
id: building-with-ai-agents
title: Building with AI Agents
sidebar_label: Building with AI Agents
---

redux-kotlin is built to be **agent-ready**. Point your coding agent at the knowledge
below and it can build Android / iOS / Web / Desktop apps on redux-kotlin with fewer
tokens, fewer write→verify cycles, and correct-by-default patterns.

## 1. Drop `AGENTS.md` in your repo

Save the block below as `AGENTS.md` at your repo root. Any agent that reads `AGENTS.md`
(Cursor, Copilot, Codex, Claude Code, …) will load it automatically.

<!-- assemble:agents-external:start -->
````markdown title="AGENTS.md"
(placeholder — generated by scripts/assemble-agent-knowledge.sh)
````
<!-- assemble:agents-external:end -->

> The copy-block uses a **4-backtick** fence so the `AGENTS.md` content's own
> 3-backtick ```` ```kotlin ```` example renders inside it instead of closing it early.

## 2. Claude Code users: install the skill (optional, premium)

Claude Code users get decision-routing + progressive disclosure over the same knowledge
via the in-repo skill. Until it ships as a plugin, copy
[`.claude/skills/redux-kotlin/`](https://github.com/reduxkotlin/redux-kotlin/tree/master/.claude/skills/redux-kotlin)
into your project's `.claude/skills/`.

## 3. Reference set

The `AGENTS.md` above links to per-concern guides on GitHub (where their source anchors
stay live). Start with
[feature-slice](https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/feature-slice.md)
and the [reference index](https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/README.md).

## 4. Verify loop

After writing code your agent should run `./gradlew build` (compile + test + detekt +
`apiCheck`) and `./gradlew detektAll`. Detail:
[testing guide](https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/testing.md).
````

> The outer page uses ```` ```` ```` (4 backticks) only here in the plan to show the nested ```` ``` ```` fence; in the actual file the copy-block is a normal triple-backtick fence as shown between the sentinels.

- [ ] **Step 2: Add the sidebar category**

In `website/sidebars.ts`, add this category object to the `docs` array (place it after the `Introduction` category):

```typescript
    {
      type: 'category',
      label: 'AI Agents',
      items: [
        'ai-agents/building-with-ai-agents',
      ],
    },
```

- [ ] **Step 3: Add the navbar link**

In `website/docusaurus.config.ts`, add this item to `themeConfig.navbar.items` (after the FAQ item):

```typescript
        {to: '/ai-agents/building-with-ai-agents', label: 'AI Agents', position: 'left'},
```

- [ ] **Step 4: Build the site to verify the page + links resolve**

Run: `cd website && yarn install --frozen-lockfile && yarn build`
Expected: `[SUCCESS] Generated static files`. The build fails on broken **internal** links (`onBrokenLinks: throw`); the page's links are external GitHub URLs (not checked) plus the sidebar/navbar route `/ai-agents/building-with-ai-agents` (must resolve). If the route errors, confirm the file path + `id` match `ai-agents/building-with-ai-agents`.

- [ ] **Step 5: Commit**

```bash
git add website/docs/ai-agents/BuildingWithAI.md website/sidebars.ts website/docusaurus.config.ts
git commit -m "feat(website): Building with AI agents page (placeholder copy-block)"
```

---

## Task 3: Assembler fills the page's copy-block from `AGENTS-external.md`

**Files:**
- Modify: `scripts/assemble-agent-knowledge.sh`

- [ ] **Step 1: Add a page-block regeneration step to the assembler**

In `scripts/assemble-agent-knowledge.sh`, after the existing `for t in $targets` loop and before the final `rc` summary, add a dedicated step that replaces the `agents-external` region in the page with a fenced copy of the assembled `docs/agent/AGENTS-external.md`. Insert:

```bash
# --- Site copy-block: fence AGENTS-external.md into the Docusaurus page ---
PAGE="website/docs/ai-agents/BuildingWithAI.md"
SRC="docs/agent/AGENTS-external.md"
if [ -f "$PAGE" ] && [ -f "$SRC" ]; then
  # Build the desired region: start marker, fenced source, end marker.
  # 4-backtick fence so the AGENTS content's own 3-backtick ```kotlin block nests cleanly.
  block="$(
    printf '%s\n' '<!-- assemble:agents-external:start -->'
    printf '````markdown title="AGENTS.md"\n'
    cat "$SRC"
    printf '````\n'
    printf '%s\n' '<!-- assemble:agents-external:end -->'
  )"
  # Replace everything between the markers (inclusive) with $block.
  rendered_page="$(awk -v repl="$block" '
    $0=="<!-- assemble:agents-external:start -->" { print repl; skip=1; next }
    $0=="<!-- assemble:agents-external:end -->"   { skip=0; next }
    !skip { print }
  ' "$PAGE")"
  if [ "$CHECK" -eq 1 ]; then
    if ! diff -q <(printf '%s\n' "$rendered_page") "$PAGE" >/dev/null; then
      echo "ASSEMBLE-FAIL: $PAGE copy-block out of sync — run scripts/assemble-agent-knowledge.sh"; rc=1
    fi
  else
    printf '%s\n' "$rendered_page" > "$PAGE"
  fi
fi
```

> This is intentionally a dedicated step (not a `MAP` entry) because it wraps the source in a code fence rather than injecting a fragment verbatim. The `agents-external` markers sit OUTSIDE the rendered fence, so they are invisible HTML comments in the published page.

- [ ] **Step 2: Run the assembler and verify the page block is filled**

Run: `bash scripts/assemble-agent-knowledge.sh`
Expected: `ASSEMBLED`. `git diff website/docs/ai-agents/BuildingWithAI.md` shows the placeholder replaced by a ```` ```markdown title="AGENTS.md" ```` fence containing the full `AGENTS-external.md` content, still wrapped by the two `agents-external` HTML-comment markers.

- [ ] **Step 3: Verify --check round-trips**

Run: `bash scripts/assemble-agent-knowledge.sh --check`
Expected: `ASSEMBLE CHECK OK` (both the MAP targets and the page copy-block are in sync).

- [ ] **Step 4: Verify the assembled page still builds**

Run: `cd website && yarn build`
Expected: `[SUCCESS] Generated static files`. The injected fence renders as a copyable code block; the HTML-comment markers do not render.

- [ ] **Step 5: Commit**

```bash
git add scripts/assemble-agent-knowledge.sh website/docs/ai-agents/BuildingWithAI.md
git commit -m "feat(agent): assemble the site copy-block from AGENTS-external.md"
```

---

## Task 4: Reference-link existence check

**Files:**
- Create: `scripts/check-site-agent-links.sh`

- [ ] **Step 1: Write the check (fails when a linked guide path is missing)**

Create `scripts/check-site-agent-links.sh`:

```bash
#!/usr/bin/env bash
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
fail=0
# Files whose GitHub blob/tree links to in-repo docs we must keep alive.
files=("docs/agent/AGENTS-external.md" "website/docs/ai-agents/BuildingWithAI.md")
for f in "${files[@]}"; do
  [ -f "$f" ] || { echo "LINK-FAIL: missing $f"; fail=1; continue; }
  # Extract repo-relative paths from github.com/reduxkotlin/redux-kotlin/(blob|tree)/<ref>/<path>
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    [ -e "$p" ] || { echo "LINK-FAIL: $f links missing repo path: $p"; fail=1; }
  done < <(grep -oE 'github\.com/reduxkotlin/redux-kotlin/(blob|tree)/[^/]+/[^)" ]+' "$f" \
            | sed -E 's#.*/(blob|tree)/[^/]+/##' | sort -u)
done
[ "$fail" -ne 0 ] && { echo "SITE-LINK CHECK FAILED"; exit 1; }
echo "SITE-LINK CHECK OK"
```

- [ ] **Step 2: Make it executable and run it (expect PASS)**

Run: `chmod +x scripts/check-site-agent-links.sh && bash scripts/check-site-agent-links.sh`
Expected: `SITE-LINK CHECK OK` (every linked `docs/agent/...` / `examples/taskflow/...` / `.claude/skills/...` path exists in the working tree).

- [ ] **Step 3: Prove it fails on a bad link (negative test)**

Run:
```bash
sed 's#references/feature-slice.md#references/does-not-exist.md#' docs/agent/AGENTS-external.md > /tmp/agc.bad && \
cp docs/agent/AGENTS-external.md /tmp/agc.good && cp /tmp/agc.bad docs/agent/AGENTS-external.md && \
bash scripts/check-site-agent-links.sh; echo "exit=$?"; cp /tmp/agc.good docs/agent/AGENTS-external.md
```
Expected: prints `LINK-FAIL: … does-not-exist.md` and `exit=1`, then restores the good file. Re-run `bash scripts/check-site-agent-links.sh` → `SITE-LINK CHECK OK`.

- [ ] **Step 4: Commit**

```bash
git add scripts/check-site-agent-links.sh
git commit -m "build(agent): site reference-link existence check"
```

---

## Task 5: Wire the link check into CI + full-gate verification

**Files:**
- Modify: `.github/workflows/agent-knowledge.yml`

- [ ] **Step 1: Read the workflow to find the anchor-check step**

Run: `sed -n '1,80p' .github/workflows/agent-knowledge.yml`
Expected: a job that runs `bash scripts/check-agent-refs.sh` (the anchor check) and the assemble check. Note the job/step structure and the step that runs `check-agent-refs.sh`.

- [ ] **Step 2: Add a step running the new link check**

In `.github/workflows/agent-knowledge.yml`, immediately after the step that runs `scripts/check-agent-refs.sh`, add a sibling step (match the existing indentation/style):

```yaml
      - name: Site reference-link existence
        run: bash scripts/check-site-agent-links.sh
```

- [ ] **Step 3: Run the full local gate**

Run each and confirm:
```bash
bash scripts/assemble-agent-knowledge.sh --check   # ASSEMBLE CHECK OK
bash scripts/check-agent-refs.sh                   # ANCHOR CHECK OK
bash scripts/check-site-agent-links.sh             # SITE-LINK CHECK OK
( cd website && yarn build )                        # [SUCCESS] Generated static files
```
Expected: all four succeed.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/agent-knowledge.yml
git commit -m "ci(agent): run site reference-link check in agent-knowledge workflow"
```

---

## Task 6: Cross-link the strategy + close the §7 loop

**Files:**
- Modify: `docs/superpowers/specs/2026-06-03-redux-kotlin-ai-integration-strategy-design.md`

- [ ] **Step 1: Mark §8 #7 resolved**

In the umbrella spec's §8, change the `#7` line from the `⛔ STILL OPEN` wording to:

```markdown
7. ✅ **External distribution mechanics** — shipped: a reduxkotlin.org "Building with AI agents"
   page + a repo-droppable external `AGENTS.md` (`docs/agent/AGENTS-external.md`), assembled from the
   shared fragments. Claude plugin (B) and template repo (C) remain deferred follow-ons. See
   `docs/superpowers/specs/2026-06-04-external-distribution-design.md`.
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-06-03-redux-kotlin-ai-integration-strategy-design.md
git commit -m "docs(strategy): mark §7 external distribution resolved"
```

---

## Self-review notes (resolved during planning)

- **Embed mechanism:** chose the bash-assembler-fenced-block over MDX `?raw`/raw-loader — Docusaurus 3.10 doesn't support imports from outside `website/`, and `?raw` support is unconfirmed; the assembler approach needs zero new deps and is consistent with the repo's existing single-source discipline. The `assemble-check` already-existing CI gate covers it for free.
- **Link ref policy:** reference-guide links target `master` (the guides post-date tag `0.6.1`, so they'd 404 at the tag); Maven coords use the published `0.6.1`. Recorded in the template + the spec's §9 open question — revisit to a pinned tag once a release includes the reference set.
- **Single-source:** `AGENTS-external.md` and the in-repo `AGENTS.md` both draw rules/modules from `_fragments/{rules,modules}.md`; the page copy-block is regenerated from `AGENTS-external.md`. One fragment edit → assembler refreshes all three → `assemble-check` enforces.
- **Out of scope (per spec §8):** Claude plugin (B), template repo (C), full on-site guide rendering, the metrics baseline (D — its own following spec).

## Open verification points for the implementer (cheap, flagged at use site)

1. Exact navbar item array + FAQ item location in `website/docusaurus.config.ts` — Task 2 Step 3.
2. The `Introduction` category location in `website/sidebars.ts` for placing the new category — Task 2 Step 2.
3. The anchor-check step's exact name/indentation in `.github/workflows/agent-knowledge.yml` — Task 5 Steps 1–2.
4. `yarn install` flag the repo uses (`--frozen-lockfile` vs plain) — match `deploy-website.yml` — Task 2 Step 4.
