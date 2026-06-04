# Redux-Kotlin AI Integration Strategy — Umbrella Design

**Date:** 2026-06-03
**Status:** Umbrella strategy (decomposes into sub-projects, each with its own spec → plan → build)
**Scope:** Strategy altitude. Defines components, dependencies, build order, success metrics, and the
best-practice questions each sub-project must resolve. Does **not** design any single component to
implementation depth — that happens per sub-project, starting with Phase 0.

**Delivery status (2026-06-04):** Phases **0a, 0, 1, 2, 3, 4 shipped.** taskflow is refactored to
package-by-feature; the full T1 reference set (all seven guides) is live under `docs/agent/references/`;
`AGENTS.md` + API-map, the Claude skill, the L4 deterministic CI (anchor check + API tripwire), the
knowledge-sync agent, and a single-source assembler are in place. Open best-practice questions (§8)
remain deferred to their own sub-projects. The §6 sequencing table below is the original plan of record.

---

## 1. Why

Software is increasingly written by agents, not humans, and increasingly autonomously. Everything that
raises agent efficiency — fewer tokens, fewer write→build→verify cycles, more correct-by-default output —
raises the value of redux-kotlin as a target an agent can build on. This initiative equips agents to build
Android / iOS / Web (wasmJs) / Desktop (JVM) applications on redux-kotlin efficiently and correctly.

**Primary consumer:** external adopters' agents (developers pulling redux-kotlin into their own apps),
**external-first** — but redux-kotlin maintainers are the first users / dogfooders. Artifacts are designed
to ship with the library and to be used in-repo.

## 2. Organizing principle

**`examples/taskflow` + Rules A–H are the canonical reference implementation.** Every knowledge artifact is
*derived from* it and stays *verifiable against* it. Nothing is hand-asserted that the codebase does not
demonstrate. This single discipline is what makes a knowledge-first strategy survivable: knowledge that
drifts from implementation is worse than no knowledge.

(Rules A–H and the full architecture live in `examples/taskflow/ARCHITECTURE.md` — render isolation,
identity split, off-main effects, delta-only status, mint-at-edge, single inset point, plus store topology,
the routing-DSL reducer model, and the offline-first sync layer.)

## 3. Strategy shape (decision record)

Three shapes were considered:

- **A. Knowledge-first** — distill patterns into `AGENTS.md` + skill + reference; agent writes code by hand,
  verifies with gradle. Cheap, fast; agent still writes boilerplate; docs can drift.
- **B. CLI / codegen-first** — generators + CLI as keystone, correct-by-construction. Biggest token payoff;
  heaviest build; rigid for novel app shapes.
- **C. Layered** — knowledge + CLI + verify, sequenced by leverage.

**Chosen: A (knowledge-first), enriched.**

**Rationale for rejecting scaffold/bootstrap codegen:** project bootstrapping and scaffold codegen are too
fragile against complex Gradle setups, drifting APIs, and per-app architecture/packaging divergence. A
pattern-informed agent handles bootstrapping *better* and adapts to app-specific shape. So scaffold/bootstrap
generation is **out of scope**. The existing compile-time KSP codegen (`@Reduce`/`@ReduxInitial` →
`ReduxModule` wiring) **stays** — it is annotation-driven, regenerated every build, never committed, and
therefore drift-proof, not fragile.

**Key existing asset:** the committed `*.api` dumps (`./gradlew apiDump`) are a machine-readable public-API
surface per module — token-cheap, accurate grounding an agent reads instead of grepping source.

## 4. Architecture

```
L1 KNOWLEDGE (keystone)          L2 CODEGEN (existing)        L3 VERIFY (documented loop)
─────────────────────────        ──────────────────────       ──────────────────────────
AGENTS.md (universal, T0)        KSP @Reduce/@ReduxInitial     compile <target>
Claude skill (premium)           (drift-proof wiring)          + commonTest / jvmTest
tiered reference (T1/T2)         keep + document;              + detektAll
API map (.api dumps)             extension = later sub-proj    + apiCheck
   ▲ all derived from taskflow + Rules A–H ▲                   → structured pass/fail

L4 DRIFT CONTROL — polices the one boundary: refs ↔ implementation
  anchor check (CI) · API-change tripwire (CI) · knowledge-sync agent (LLM)
```

**Agent data flow when building an app:** load `AGENTS.md` (always) → on task, pull the relevant tiered
reference section + the target module's `.api` dump → write code following patterns → run the L3 verify
commands → self-correct on failure. KSP handles reducer wiring invisibly.

## 5. Components

### 5.1 — Packaging best-practice (cross-cutting decision)

The reference set recommends **package-by-feature** as the default app organization, flexibly:

- **Greenfield → package-by-feature** (default): co-locate a feature's `Models · Actions · Reducer ·
  Effects · Screen · Selectors · Tests` under `feature/<name>/`. Better modularity, and **better agent
  context-locality** (one directory per feature → fewer files loaded, fewer tokens hunting across layers).
- **Existing app → match its existing convention** (don't impose).
- **Tiny / single-screen → package-by-layer acceptable.**

Correctness clarifications this forces (all improvements, must be stated in the guides):
- **Rule E is a discipline, not one file.** Effects originate only from middleware (never reducers/UI); a
  feature-based app splits per-feature effect handlers and *composes* them into the middleware.
- **Sealed `Action` leaves may live across feature packages** within the same module (Kotlin 1.5+).
- **KSP `@Reduce` is packaging-agnostic** — top-level annotated handlers are discovered regardless of
  package, so per-feature co-location works naturally.

Consequence: taskflow (currently package-by-layer) is refactored to package-by-feature in **Phase 0a** so
the canon matches this recommendation and the Phase 0 exemplar can cite it for organization, not just for
packaging-independent element patterns.

### L1 — Knowledge (keystone)

**Single-source discipline:** author the knowledge **once** as the per-concern reference set; *assemble*
`AGENTS.md` and the Claude skill from it. Do not maintain parallel copies. This collapses the drift surface
to a single boundary (refs ↔ implementation).

- **(a) `AGENTS.md`** — universal, tool-agnostic (Cursor / Copilot / Codex / Claude). Always in context, so
  token-tight. Contents = the irreducible index: what redux-kotlin is (1 para) · module map (which module
  for which job) · build/test/lint-gate commands · Rules A–H as one-liners · "where things live" layout ·
  pointers into T1/T2. The existing `CLAUDE.md` is approximately this but maintainer-scoped; see open
  question 6 on unification.
- **(b) Claude skill** — premium Claude Code path. `SKILL.md` = rules card + **decision routing** ("building
  a feature? → `references/feature-slice.md`"; "wiring persistence? → `references/platform-shims.md`").
  `references/` = the T1/T2 deep-dives. The skill *is* the progressive-disclosure reference for Claude users
  — no separate artifact.
- **(c) Tiered reference** (the markdown the skill points to; also serves non-Claude tools):
  - **T0** (always, embedded in `AGENTS.md`): rules card + module map + commands.
  - **T1** (on task): per-concern guides — *feature slice · store setup & topology · Compose binding
    (Rule C) · effects + sync · testing · the 6 platform shims · modularization*.
  - **T2** (on demand): `ARCHITECTURE.md` deep-dive + `.api` dumps.
- **(d) API map** — an index mapping each module → its `.api` dump path, so an agent reads the committed
  public-API surface instead of source.

### L2 — Codegen (existing, keep)

Document the KSP `@Reduce`/`@ReduxInitial` flow inside the T1 feature/testing guides. Extension (multi-model
`onAction`, effect handlers) is flagged as a **separate later sub-project**, not in this initiative's scope.
**No scaffolding, no bootstrap generation.**

### L3 — Verify (documented loop, no binary)

A T1 guide describing the tier-1 sequence an agent runs after writing code:
`compile <target>` → `commonTest`/`jvmTest` → `detektAll` → `apiCheck`, with how to read failures and the
host-gating caveats (iOS simulator needs a Mac with the Xcode SDK; trust CI for cross-platform).
Higher verification tiers — headless screenshot, UI-behavior tests, state-machine assertions — are **open
questions** (section 8.3), not designed here.

### L4 — Drift control (refs ↔ implementation)

Committed:

- **Anchor check (CI, deterministic):** refs cite `path:line`, module names, and gradle commands. CI
  verifies cited paths exist, module names match `settings.gradle.kts`, and cited commands are real tasks.
- **API-change tripwire (CI, deterministic):** any `*.api` file changing in a PR flags "public API changed —
  knowledge review required" and lists affected modules; blocks until the API-map index is regenerated / a
  review label is set.
- **knowledge-sync agent (LLM):** PR-gated (triggers on changes to `redux-kotlin*/src`, `*.api`, or
  `examples/taskflow`) or scheduled. Diffs the change against the knowledge refs and comments which refs need
  updating, or opens a follow-up. Catches the semantic / prose / pattern drift the deterministic checks
  cannot see. Can phase in after the cheap checks.

Parked (stretch): **snippet compile-check** — extract fenced Kotlin from refs and compile it in a test
source set (kotlinx-knit style) so doc code cannot rot. Strongest guard, most setup.

**Provenance discipline (enabler for all of L4):** every reference doc carries a header naming what it
derives from (taskflow paths, specific `.api` files, which Rules). The anchor checker and sync agent diff
against exactly those — detection is targeted, not whole-repo guesswork.

## 6. Sequencing

Each phase is its own sub-project (spec → plan → build). Ordered so value lands early:

| Phase | Deliverable | Notes |
|---|---|---|
| **0a** *(prereq)* | **taskflow feature-based refactor** | Refactor `examples/taskflow` from package-by-layer to **package-by-feature** so the canon matches the recommended packaging (see §5.1). Blocks the clean Phase 0 exemplar (avoids re-anchoring churn). Behavior-preserving; build stays green; `ARCHITECTURE.md` updated. Own spec → plan → build. |
| **0** | Reference set + provenance headers | Keystone-of-keystone. T1 per-concern guides derived from (post-refactor) taskflow / Rules / `.api`; folds in the L2 codegen doc + L3 verify guide. Unblocks everything. |
| **1** | `AGENTS.md` (T0) + API-map index | Assembled from the refs. Immediately useful to any agent/tool. |
| **2** | Claude skill | `SKILL.md` + decision routing + `references/` wired to the T1/T2 set. |
| **3** | L4 deterministic CI | Anchor check + API-change tripwire (needs refs to exist first). |
| **4** | knowledge-sync agent | Semantic drift catch. |

**First sub-project to brainstorm next:** Phase 0.

## 7. Success metrics (token + speed framing)

- **Token:** tokens-to-correct-feature-slice — cold agent vs knowledge-equipped, baselined against a
  taskflow-style slice.
- **Speed:** write→verify cycles to green (fewer when patterns are preloaded).
- **Correctness:** % Rules A–H adherence in agent output (render isolation, mint-at-edge, off-main) —
  reviewable / lintable.
- **Drift:** time-to-detect API/pattern drift (target: within the PR, via L4); count of stale refs caught.
- **Adoption (external north star):** external repos consuming `AGENTS.md` / the skill.

## 8. Open best-practice questions

Resolved per sub-project, not here:

1. **Store-topology decision rule** — when 1 store vs taskflow's 2-store + `AccountRegistry`?
2. **Module-selection table** — concurrent vs threadsafe vs core; when granular / multimodel.
3. **Verify-tier standardization** — which of headless-screenshot / UI-behavior / state-machine become
   standard (overlap risk flagged; tier-1 is the only committed scope today).
4. **KSP extension scope** — multi-model `onAction` / effect handlers: a separate later sub-project.
5. **Skill granularity** — one skill vs several (feature / store / testing).
6. **`AGENTS.md` ↔ `CLAUDE.md`** — unify (one generated source, maintainer + app-builder views) or split.
7. **External distribution mechanics** — how `AGENTS.md` / the skill ship with the published library (docs
   page? template repo? part of the artifact?).

## 9. Explicitly out of scope

- Scaffold / project-bootstrap codegen (too fragile; agents do this better when pattern-informed).
- A standalone CLI binary (no `rk` binary; verify is documented commands, not tooling).
- KSP extension beyond the current `@Reduce`/`@ReduxInitial` v1.
- Designing verification tiers 2–4.

These can be revisited if the knowledge-first approach proves insufficient on the success metrics.
