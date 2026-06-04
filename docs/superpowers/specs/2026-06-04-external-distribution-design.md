# Redux-Kotlin AI Knowledge — External Distribution — Design

**Date:** 2026-06-04
**Status:** Design (approved for implementation planning)
**Scope:** Resolve open question #7 of the AI-integration umbrella spec — how the agent knowledge
(`AGENTS.md` / skill / reference set) reaches **external adopters' agents**. Ships the primary channel
(a published docs page + a copy-into-repo `AGENTS.md` template). Does **not** build the Claude
plugin/marketplace package, a template repo, or the metrics baseline — those are separate efforts.

**Parent:** `docs/superpowers/specs/2026-06-03-redux-kotlin-ai-integration-strategy-design.md` (§7).

---

## 1. Why

The AI-integration strategy is explicitly **external-first** — its primary consumer is an external
adopter's coding agent pulling redux-kotlin into their own app. Today the entire knowledge surface
(`AGENTS.md`, the Claude skill, the eight T1 reference guides, `api-map.md`) lives **in-repo only**.
An external adopter has no published way to point their agent at it. The strategy's central bet is
unrealized until the knowledge is reachable from outside the repo. This is the largest remaining
strategic gap (umbrella §8 #7).

## 2. Decision record — channel

Four channels were considered: (A) published docs-site page, (B) installable Claude plugin/skill,
(C) template/starter repo, (D) copy-into-repo instructions.

**Chosen: A as the canonical primary, with D folded into the same page.** B is a later premium
add-on; C is deferred.

- **A over B:** the strategy is tool-agnostic — `AGENTS.md` is the universal artifact, the Claude skill
  is the *premium* path. A docs page reaches Cursor / Copilot / Codex / Claude alike; a Claude plugin
  reaches only Claude, so it cannot be the primary channel.
- **A over C:** a template repo helps only greenfield apps and is the heaviest to keep in sync with the
  libraries; most adopters integrate into an existing app.
- **A + D are one artifact:** a published page an agent can read by URL that *also* doubles as the
  copy-source so the adopter drops `AGENTS.md` + the skill into their own repo (agent-locality).

## 3. What ships — the "Building with AI agents" page

A new Docusaurus section on reduxkotlin.org containing, in order:

1. **Why** — one paragraph: redux-kotlin is agent-ready; what equipping your agent buys (fewer tokens,
   fewer write→verify cycles, correct-by-default patterns).
2. **The `AGENTS.md` block** — the copy-ready external template (see §4), with "drop this at your repo
   root so any agent loads it."
3. **Claude Code users** — install-the-skill steps (the premium path); a short note that the skill adds
   decision-routing + progressive disclosure over the same knowledge.
4. **Reference set** — links to the GitHub-hosted guides (where the `path → Symbol` anchors stay live),
   `api-map.md`, and `examples/taskflow/ARCHITECTURE.md`.
5. **Verify loop** — the gradle commands an agent runs after writing code (compile → test → detekt →
   apiCheck), pointing at `testing.md` for detail.

The page is an **entry-point**, not a rendered copy of the guides: the guides remain canonical in
`docs/agent/` and render on github.com with live anchors; the page links to them.

## 4. The external `AGENTS.md` template

The published `AGENTS.md` block is **distinct from the repo's `AGENTS.md`**. The in-repo one is
maintainer-scoped — it points at `docs/agent/references/`, `examples/taskflow`, `CLAUDE.md`, which an
external adopter's repo does not have. The external template instead:

- States what redux-kotlin is (one paragraph), the **module map**, and **Rules A–H** — these come from
  the shared `docs/agent/_fragments/{modules,rules}.md` (the same source the repo `AGENTS.md` uses), so
  the substance never diverges.
- Points at **published URLs**: the GitHub-hosted reference set
  (`https://github.com/reduxkotlin/redux-kotlin/blob/<ref>/docs/agent/references/<guide>.md`), the
  Maven Central coordinates, and reduxkotlin.org.
- Gives the build/verify gate commands (generic, not repo-specific paths).

Canonical committed output: `docs/agent/AGENTS-external.md`. This is the file an adopter copies (the
site embeds it verbatim — §5).

**`<ref>` policy:** links target a **released tag** (e.g. the current published library version), not
`master`, so an adopter's pinned library version matches the guides they read. The tag is a single
variable in the assembler skeleton (see §9 open question on automation).

## 5. Single-sourcing & drift control

Mirrors the repo's existing L4 discipline; no new mechanism class.

1. **Assembler extension** — `scripts/assemble-agent-knowledge.sh` gains a second output:
   `docs/agent/AGENTS-external.md`, produced by injecting `_fragments/{rules,modules}.md` into an
   external skeleton (external pointers + boilerplate). Repo-`AGENTS.md` and `AGENTS-external.md` share
   one fragment source.
2. **Site embed** — the page (`website/docs/ai-agents/index.md` or `.mdx`) embeds `AGENTS-external.md`
   between assemble markers, so the on-site copy-block equals the committed template byte-for-byte. The
   assembler writes this block too.
3. **CI enforcement** — the existing **`assemble-check`** (`assemble-agent-knowledge.sh --check`, run by
   the `Assemble check` workflow) extends to verify both new outputs. Edit a fragment without
   re-assembling → CI fails, identical to today's guard.
4. **Reference-link existence check** — a small check (folded into `scripts/check-agent-refs.sh` or a
   sibling) asserts every GitHub reference-guide path the page links actually exists in the repo, so a
   renamed/removed guide fails CI rather than rotting a published link. (Docusaurus `onBrokenLinks:
   throw` guards only *internal* links; these are external GitHub URLs.)
5. **Website build** — `yarn build` (the existing CI guard) covers the new page + sidebar entry.

## 6. Page placement & navigation

- New top-level docs category **"AI agents"** in the Docusaurus sidebar (sibling to Basics / Advanced),
  with a single page `index.md` for now (room to grow into per-channel pages later).
- A navbar link so it is discoverable from the site header.
- Follow the existing `website/docs/*` front-matter + sidebar conventions; do not restructure unrelated
  docs.

## 7. Verification

The CI gates are the tests (this is a docs/build feature, no unit tests):

- `bash scripts/assemble-agent-knowledge.sh --check` → in sync (repo-AGENTS + AGENTS-external + site block).
- the reference-link existence check → all linked guides exist.
- `cd website && yarn build` with `onBrokenLinks: throw` → the page builds, internal links resolve.
- `bash scripts/check-agent-refs.sh` → unaffected guides still anchor-clean.

## 8. Explicitly out of scope

- **B** — Claude plugin / marketplace packaging of the skill (separate follow-on).
- **C** — template / starter repo.
- Full on-site rendering of the eight reference guides (the transform/anchor-rot cost the entry-point
  approach was chosen to avoid).
- Full `AGENTS.md` ↔ `CLAUDE.md` unification (#6) — this takes only the first bite: two AGENTS
  audiences, one fragment source. Broader unification is its own question.
- **Metrics baseline (D)** — the tokens-to-correct-slice cold-vs-equipped measurement. Its own
  immediately-following sibling spec → plan; not built here.

## 9. Open questions (resolve in planning)

1. **Released-tag automation** — should the external links' `<ref>` be hardcoded to the current release
   tag and bumped by the release process, or derived (e.g. from the version catalog / a git describe at
   assemble time)? Hardcode-with-release-bump is the simpler v1; confirm the repo's release flow can
   own the bump.
2. **Maven coordinates surface** — confirm the exact current published group/artifact/version to put in
   the external template (from the publishing config / a recent release).
3. **Skill install instructions** — pin the exact mechanism Claude Code users follow to install the
   in-repo skill externally today (manual copy of `.claude/skills/redux-kotlin/` vs a plugin) — if no
   clean external install exists yet, the page links the skill on GitHub and notes the plugin is
   forthcoming (channel B).
4. **Page route** — `ai-agents` vs `agents` vs nesting under an existing category; pick to match the
   sidebar/route conventions in `website/sidebars*.js`.
