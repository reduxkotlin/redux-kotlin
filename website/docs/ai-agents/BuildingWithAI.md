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

{/* assemble:agents-external:start */}
````markdown title="AGENTS.md"
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

- `redux-kotlin` — core contract: `Store`/`TypedStore`, `Reducer`, `Middleware`, `createStore`, `applyMiddleware`, `combineReducers`, `compose`.
- `redux-kotlin-threadsafe` — `createThreadSafeStore` (atomicfu-locked store wrapper).
- `redux-kotlin-concurrent` — `createConcurrentStore` (lock-free reads + reentrant-lock-serialized writes; the CallerSerialized strategy).
- `redux-kotlin-granular` — `subscribeTo` / `subscribeFields` field-level subscriptions.
- `redux-kotlin-registry` — `StoreRegistry` / `TypedStoreRegistry` keyed multi-store container.
- `redux-kotlin-multimodel` — `ModelState` typesafe heterogeneous model bag.
- `redux-kotlin-multimodel-granular` — granular subscriptions for `ModelState`.
- `redux-kotlin-compose` — Compose `State<T>` bindings (`fieldState`, `selectorState`, `StableStore`).
- `redux-kotlin-compose-multimodel` — Compose bindings for `ModelState`.
- `redux-kotlin-compose-saveable` — `StateSaver` + `Store<S>.rememberSaveableState` store-anchored snapshot persistence (rotation + process death) via `SaveableStateRegistry`.

Full module → public-API index:
https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/api-map.md

## Design rules

Faithful one-liners from the canonical sample app (`examples/taskflow`):

- **Rule C — Render isolation.** No composable reads a model (board/cards/columns) wholesale; every leaf binds the narrowest slice via `selectorState`/`fieldStateOf` and is wrapped in `key(...)`; list derivation lives in pure functions/reducers.
- **Rule D — Identity split.** A profile edit fans `EditProfile` to the root account directory, the per-account `CollaboratorsModel`, and `SessionModel` (bio) — identity is never duplicated inconsistently.
- **Rule E — Off-main effects.** Effects originate only in middleware and run off-main; dispatch marshals back to main via `NotificationContext` (no explicit main hop). Per-feature handlers compose into one `effectsMiddleware`.
- **Rule F — Delta-only status.** `SyncEngine` emits `onStatus` only on a real `SyncStatus` change.
- **Rule G — Mint at the edge.** Ids and timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — Single inset point.** Window insets are applied once at the shell root.
- **Rule I — State-keyed lifecycle effects.** Screen-data loads key on **state** (the nav-derived slice, e.g. `BoardLifecycleEffect` on `nav.activeBoardId`), never on navigation events — state-only entry points (process-death restore, deep links, DevTools time-travel, account-switch hydration) set state without replaying events, so an event-keyed load silently never runs. Fallback: match the hydrating action in middleware.

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
- Store consistency model (sync writes, async notify) — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/store-consistency-model.md
- State persistence & restore (process death, preloadedState, saveable) — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/state-persistence.md
- Snapshot / golden UI testing — https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/snapshot.md

## DevTools CLI — `rk`

The unified `rk` binary bundles `rk devtools` (bridge receiver + capture queries) and
`rk snapshot` (headless Compose renderer).

**Install — Homebrew/Scoop (bundled JRE, no Java required):**

```bash
# macOS / Linux
brew install reduxkotlin/tap/rk

# Windows
scoop bucket add reduxkotlin https://github.com/reduxkotlin/scoop-bucket
scoop install rk
```

**From source (any OS, needs JDK 17+):**

```bash
git clone https://github.com/reduxkotlin/redux-kotlin.git
cd redux-kotlin
./gradlew :redux-kotlin-cli:installDist
# binary: redux-kotlin-cli/build/install/rk/bin/rk
```

Add `redux-kotlin-cli/build/install/rk/bin` to your `PATH`, then use:
- `rk devtools serve` — receive a running app's action stream; `rk devtools actions|diff|state|tail` to query.
- `rk snapshot --scene <name> --preset <name> --out shot.png` — render a Compose screen from state.

Full guide: https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/devtools.md

> **Note:** `rk snapshot` only renders the binary's built-in scenes. To render
> **your own app's screens**, depend on `redux-kotlin-snapshot` as a library and
> call `yourRegistry.runCli(args)` from your `main` (then `exitProcess(0)` — Skiko
> leaves non-daemon threads alive).

## Verify loop

After writing code, run (Gradle): build (`./gradlew build`), lint
(`./gradlew detektAll`), and — if you publish a library module — `./gradlew apiCheck`.
`explicitApi()` projects need a KDoc on every public declaration.
````
{/* assemble:agents-external:end */}

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
