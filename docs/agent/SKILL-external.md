---
name: redux-kotlin
description: Use when building or reviewing redux-kotlin (Kotlin Multiplatform Redux) code — adding/editing a feature slice, wiring the store, Compose state binding, effects/sync middleware, testing, platform shims, or modularization.
---

# redux-kotlin

Kotlin Multiplatform port of the Redux contract: a minimal core (`redux-kotlin`) plus opt-in
companion modules on one `Store<S>` contract. Recommended app organization is **package-by-feature**
(`feature/<name>/` slice + shared `core` · `infra` · `app` · `ui`).

## Always-apply rules

<!-- assemble:rules:start -->
- **Rule C — Render isolation.** No composable reads a model (board/cards/columns) wholesale; every leaf binds the narrowest slice via `selectorState`/`fieldStateOf` and is wrapped in `key(...)`; list derivation lives in pure functions/reducers.
- **Rule D — Identity split.** A profile edit fans `EditProfile` to the root account directory, the per-account `CollaboratorsModel`, and `SessionModel` (bio) — identity is never duplicated inconsistently.
- **Rule E — Off-main effects.** Effects originate only in middleware and run off-main; dispatch marshals back to main via `NotificationContext` (no explicit main hop). Per-feature handlers compose into one `effectsMiddleware`.
- **Rule F — Delta-only status.** `SyncEngine` emits `onStatus` only on a real `SyncStatus` change.
- **Rule G — Mint at the edge.** Ids and timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — Single inset point.** Window insets are applied once at the shell root.
- **Rule I — State-keyed lifecycle effects.** Screen-data loads key on **state** (the nav-derived slice, e.g. `BoardLifecycleEffect` on `nav.activeBoardId`), never on navigation events — state-only entry points (process-death restore, deep links, DevTools time-travel, account-switch hydration) set state without replaying events, so an event-keyed load silently never runs. Fallback: match the hydrating action in middleware.
<!-- assemble:rules:end -->

Full architecture and rules:
https://github.com/reduxkotlin/redux-kotlin/blob/master/examples/taskflow/ARCHITECTURE.md

## Decision routing

| If you are… | Read |
|---|---|
| Adding or editing a **feature** | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/feature-slice.md |
| Setting up the **store / topology** | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/store-setup.md |
| **Compose** state binding (Rule C) | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/compose-binding.md |
| **Effects + sync** (Rule E) | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/effects-sync.md |
| **Testing** + the verify loop | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/testing.md |
| **Visual / golden UI** verification | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/snapshot.md |
| The 5 **platform shims** | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/platform-shims.md |
| **Modularization** | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/modularization.md |
| **Debugging** a running app | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/devtools.md |
| **Persisting/restoring state** | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/state-persistence.md |
| **Store consistency model** | https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/store-consistency-model.md |

## Pointers

- Project guide: https://reduxkotlin.org/agent-setup/AGENTS.md
- Public API surface: https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/api-map.md
- Reference index: https://github.com/reduxkotlin/redux-kotlin/blob/master/docs/agent/references/README.md

Build/lint gate: `./gradlew build`, `./gradlew detektAll`, and `./gradlew apiCheck` for public API changes.
Never bypass verification. Projects using `explicitApi()` need an explicit modifier and KDoc on every public declaration.
