---
name: redux-kotlin
description: Use when building or reviewing redux-kotlin (Kotlin Multiplatform Redux) code — adding/editing a feature slice, wiring the store, Compose state binding, effects/sync middleware, testing, the platform expect/actual shims, or modularization. Enforces the project's render-isolation / off-main-effects / mint-at-edge rules.
---

# redux-kotlin

Kotlin Multiplatform port of the Redux contract: a minimal core (`redux-kotlin`) plus opt-in
companion modules on one `Store<S>` contract. Recommended app organization is **package-by-feature**
(`feature/<name>/` slice + shared `core` · `infra` · `app` · `ui`); the canonical example is `examples/taskflow`.

## Always-apply rules (full text → `../../../examples/taskflow/ARCHITECTURE.md` §17)

- **Rule C — Render isolation.** No composable reads a model wholesale; bind the narrowest slice via `selectorState`/`fieldStateOf`, wrapped in `key(...)`. Derivation lives in pure functions/reducers.
- **Rule D — Identity split.** A profile edit fans to the root account directory, per-account `CollaboratorsModel`, and `SessionModel` — never duplicate identity inconsistently.
- **Rule E — Off-main effects.** Effects originate only in middleware and run off-main; dispatch marshals back to main via `NotificationContext`. Per-feature handlers compose into one `effectsMiddleware`.
- **Rule F — Delta-only status.** Emit status only on a real change.
- **Rule G — Mint at the edge.** Ids/timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — Single inset point.** Window insets applied once at the shell root.

## Decision routing

| If you are… | Read |
|---|---|
| Adding or editing a **feature** | [`feature-slice.md`](../../../docs/agent/references/feature-slice.md) |
| Setting up the **store / topology** | `../../../docs/agent/references/store-setup.md` *(planned — see README)* |
| **Compose** state binding (Rule C) | `../../../docs/agent/references/compose-binding.md` *(planned)* |
| **Effects + sync** (Rule E) | `../../../docs/agent/references/effects-sync.md` *(planned)* |
| **Testing** + the verify loop | `../../../docs/agent/references/testing.md` *(planned)* |
| The 6 **platform shims** | `../../../docs/agent/references/platform-shims.md` *(planned)* |
| **Modularization** | `../../../docs/agent/references/modularization.md` *(planned)* |

## Pointers

- T0 index (modules, commands, layout): [`AGENTS.md`](../../../AGENTS.md)
- Public API surface per module: [`api-map.md`](../../../docs/agent/api-map.md)
- Architecture deep-dive: [`ARCHITECTURE.md`](../../../examples/taskflow/ARCHITECTURE.md)
- Reference index: [`references/README.md`](../../../docs/agent/references/README.md)

Build/lint gate (never `--no-verify`; `explicitApi()` needs KDoc on every public decl): `./gradlew build`, `./gradlew detektAll`, `./gradlew apiCheck`. Detail → `../../../CLAUDE.md`.
