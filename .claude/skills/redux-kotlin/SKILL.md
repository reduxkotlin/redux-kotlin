---
name: redux-kotlin
description: Use when building or reviewing redux-kotlin (Kotlin Multiplatform Redux) code — adding/editing a feature slice, wiring the store, Compose state binding, effects/sync middleware, testing, the platform expect/actual shims, or modularization. Enforces the project's render-isolation / off-main-effects / mint-at-edge rules.
---

# redux-kotlin

Kotlin Multiplatform port of the Redux contract: a minimal core (`redux-kotlin`) plus opt-in
companion modules on one `Store<S>` contract. Recommended app organization is **package-by-feature**
(`feature/<name>/` slice + shared `core` · `infra` · `app` · `ui`); the canonical example is `examples/taskflow`.

## Always-apply rules (full text → `../../../examples/taskflow/ARCHITECTURE.md` §17)

<!-- assemble:rules:start -->
- **Rule C — Render isolation.** No composable reads a model (board/cards/columns) wholesale; every leaf binds the narrowest slice via `selectorState`/`fieldStateOf` and is wrapped in `key(...)`; list derivation lives in pure functions/reducers.
- **Rule D — Identity split.** A profile edit fans `EditProfile` to the root account directory, the per-account `CollaboratorsModel`, and `SessionModel` (bio) — identity is never duplicated inconsistently.
- **Rule E — Off-main effects.** Effects originate only in middleware and run off-main; dispatch marshals back to main via `NotificationContext` (no explicit main hop). Per-feature handlers compose into one `effectsMiddleware`.
- **Rule F — Delta-only status.** `SyncEngine` emits `onStatus` only on a real `SyncStatus` change.
- **Rule G — Mint at the edge.** Ids and timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — Single inset point.** Window insets are applied once at the shell root.
<!-- assemble:rules:end -->

## Decision routing

| If you are… | Read |
|---|---|
| Adding or editing a **feature** | [`feature-slice.md`](../../../docs/agent/references/feature-slice.md) |
| Setting up the **store / topology** | [`store-setup.md`](../../../docs/agent/references/store-setup.md) |
| **Compose** state binding (Rule C) | [`compose-binding.md`](../../../docs/agent/references/compose-binding.md) |
| **Effects + sync** (Rule E) | [`effects-sync.md`](../../../docs/agent/references/effects-sync.md) |
| **Testing** + the verify loop | [`testing.md`](../../../docs/agent/references/testing.md) |
| The 5 **platform shims** | [`platform-shims.md`](../../../docs/agent/references/platform-shims.md) |
| **Modularization** | [`modularization.md`](../../../docs/agent/references/modularization.md) |

## Pointers

- T0 index (modules, commands, layout): [`AGENTS.md`](../../../AGENTS.md)
- Public API surface per module: [`api-map.md`](../../../docs/agent/api-map.md)
- Architecture deep-dive: [`ARCHITECTURE.md`](../../../examples/taskflow/ARCHITECTURE.md)
- Reference index: [`references/README.md`](../../../docs/agent/references/README.md)

Build/lint gate (never `--no-verify`; `explicitApi()` needs KDoc on every public decl): `./gradlew build`, `./gradlew detektAll`, `./gradlew apiCheck`. Detail → `../../../CLAUDE.md`.
