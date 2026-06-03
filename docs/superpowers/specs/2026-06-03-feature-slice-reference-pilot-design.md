# Phase 0-pilot: `feature-slice.md` + Reference-Set Conventions

**Date:** 2026-06-03
**Phase:** 0-pilot of the redux-kotlin AI integration strategy (the keystone Phase 0, scoped pilot-first).
**Branch:** `feature-slice-pilot` (off latest `master`), worktree-isolated.
**Type:** Documentation authoring. Creates agent-facing knowledge under `docs/agent/`; no source/build changes.

## Why

Phase 0 is the keystone of the strategy: a single-source T1 reference set that Phase 1 (`AGENTS.md`) and Phase 2 (Claude skill) assemble from. Authoring all seven guides at once risks propagating format mistakes across seven docs before review. This pilot authors the **first** guide (`feature-slice.md` — the exemplar that the now-merged package-by-feature taskflow refactor, Phase 0a, unblocked) and **locks the conventions** (provenance header, anchor style, tier/dir layout, template) that the remaining six guides (`0-rest`) replicate.

Prereq satisfied: Phase 0a is merged to `master` (`examples/taskflow` is package-by-feature: `core/ infra/ feature/ app/ ui/`).

## Decisions (settled in brainstorming)

- **Location:** canonical refs live at `docs/agent/references/` — neutral, versioned, tool-agnostic; out of `website/` (agent-facing, not end-user) and out of `.claude/` (detekt-excluded + Claude-coupled). Both `AGENTS.md` (Phase 1) and the Claude skill (Phase 2) point INTO this location (single-source).
- **Exemplar:** `feature.boardlist` as the end-to-end spine (complete slice without board's heavy optimistic/sync/undo machinery); `feature.board` referenced in callouts for advanced concerns.
- **Anchor style:** `path → symbol` (e.g. `feature/board/BoardReducers.kt → boardReducer`). Resilient to line drift (the churn Phase 0a avoided); the future L4 anchor checker verifies the path exists AND the symbol is present.

## Deliverables

1. **`docs/agent/references/feature-slice.md`** — the worked guide (structure below).
2. **`docs/agent/references/_template.md`** — provenance-header + section skeleton for the remaining six guides to copy.
3. **`docs/agent/references/README.md`** — index of the seven planned T1 guides with status (feature-slice ✅; store-setup, compose-binding, effects-sync, testing, platform-shims, modularization — planned), so cross-references resolve and the set is legible.

## Provenance header (the format all guides reuse)

YAML frontmatter at the top of every reference guide. This is exactly what the Phase-3 L4 anchor checker will parse, so the format is a deliverable, not decoration.

```yaml
---
tier: T1
concern: feature-slice
derives_from:                       # path → symbol (line-drift-resilient)
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → BoardListModel, boardListReducer, BoardListScreen, BoardListActions
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/EffectsMiddleware.kt → effectsMiddleware
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/CardActions.kt → InverseOp
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → declareAccountModels
api_files:                          # taskflow is convention.control (NO .api); cite the library .api it consumes
  - redux-kotlin-compose-multimodel/api/redux-kotlin-compose-multimodel.klib.api
  - redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api
rules: [C, E, G]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 06214a9, date: 2026-06-03 }
---
```

Field intent: `derives_from` = the source-of-truth this doc paraphrases (anchor-checked); `api_files` = committed public-API surface backing the APIs used; `rules` = which of Rules A–H the guide enforces; `assembles_into` = downstream consumers (single-source map); `last_verified` = the revision the citations were checked against.

## feature-slice.md structure

A feature author's walkthrough. Opens with a one-paragraph "what is a feature slice here" (package-by-feature; one dir `feature/<name>/`; what lives in the slice vs. the shared `core` kernel / `infra` / `app` / `ui`). Then **the seven elements**, each as: *what it is · where it lives · which Rule it honors · `path → symbol` citation* (boardlist spine; board callout where the boardlist case is too simple):

1. **Models** — feature slot model in `feature/<name>/`; domain entities in the `core` kernel. Cite `feature/boardlist → BoardListModel`, `core/BoardEntities.kt → BoardSummary`.
2. **Actions** — feature actions in the slice; card-mutation / sync-contract actions + `InverseOp` in `core`; **why `Action`/`Undoable` are plain marker interfaces, not `sealed`** (Kotlin same-package rule; the Phase-0a finding). Cite `feature/boardlist → BoardListActions`, `core/Action.kt`, `core/CardActions.kt → InverseOp`.
3. **Reducer** — the hand-written `model<M> { on<T> { … } }` routing DSL; pure; **Rule G** (mint ids/timestamps at the dispatch edge, never in the reducer). Cite `feature/boardlist → boardListReducer`.
4. **Effects** — **Rule E** (effects originate only in middleware, never reducers/UI); the feature's effect handler is **composed into** `effectsMiddleware`, runs off-main. boardlist's load is handled there → board callout. Cite `feature/board/EffectsMiddleware.kt → effectsMiddleware`.
5. **Screen** — Compose; **Rule C** render isolation (bind the narrowest slice via `selectorState`/`fieldState`, never read a whole model wholesale). Cite `feature/boardlist → BoardListScreen`; board callout for richer binding.
6. **Selectors** — pure derivation functions for rendering. Cite `feature/board/BoardSelectors.kt` (board callout; boardlist's derivation is inline).
7. **Tests** — `commonTest` is the default home; `jvmTest` for jvm-only concerns. Cite a boardlist/board reducer test.

Then:
- **Store wiring** — register the feature's model slot (declaration order matters) and compose its effect handler (middleware order `activityLogger → undo → effects` matters). Cite `app/AccountStore.kt → declareAccountModels`, `app/AppStore.kt`.
- **Verify loop** (L3 fold-in, brief) — `compile <target> → commonTest/jvmTest → detektAll → apiCheck`; how to read a failure; iOS-simulator host-gating caveat (trust CI). Full treatment deferred to the `testing` guide.
- **Codegen note** (L2 fold-in, brief) — KSP `@Reduce` is packaging-agnostic (top-level annotated handlers discovered regardless of package). Full treatment deferred to feature/testing guides.
- **"Where things live"** mini-map — `core` (kernel) · `infra` · `feature/<name>` · `app` (composition root) · `ui` — and forward cross-references to the other six guides (via `README.md`).

Tone/length budget (a convention the template encodes): tight, scannable, citation-dense; target ~250–400 lines; prose explains the *why/rule*, citations carry the *where*.

## Acceptance gate

Docs-only sub-project — no Kotlin compile/test. The gate is **citation integrity**: every `path → symbol` in `feature-slice.md` (and the header `derives_from`) is verified to resolve — the cited path exists under the worktree AND the symbol appears in that file. Implemented as a grep sweep over the citation list; this is a manual dry-run of the Phase-3 anchor checker and validates the chosen anchor style end-to-end. Also confirm `api_files` paths exist.

Secondary checks: no broken intra-`docs/agent/` markdown links (README ↔ guide ↔ template); `last_verified.commit` matches the worktree base.

## Out of scope

- The other six T1 guides, the API-map index, `AGENTS.md`, the Claude skill, the automated L4 anchor checker / CI (all later sub-projects).
- Any change to `examples/taskflow` source or to `website/`.

## Next

After approval → writing-plans for the authoring steps, then execute with the citation-integrity gate, then PR. On completion, `0-rest` replicates the locked template across the remaining six guides.
