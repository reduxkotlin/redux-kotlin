# AGENTS.md

Token-tight index for agents working in redux-kotlin. Read this first; it points
to depth, it does not inline it.

## What redux-kotlin is

A Kotlin Multiplatform port of the Redux contract: a deliberately minimal core
(`redux-kotlin`) holding the `Store<S>` contract, plus opt-in companion modules
that layer thread-safety, concurrency, granular subscriptions, multi-store
registries, a heterogeneous model bag, and Compose bindings — all sharing the
same single `Store<S>` contract. Take the core, add only the companions you need.

## Module map

The ten published core modules (each "use for X"):

<!-- assemble:modules:start -->
- `redux-kotlin` — core contract: `Store`/`TypedStore`, `Reducer`, `Middleware`, `createStore`, `applyMiddleware`, `combineReducers`, `compose`.
- `redux-kotlin-threadsafe` — `createThreadSafeStore` (atomicfu-locked store wrapper). **Deprecated** — prefer `redux-kotlin-concurrent`.
- `redux-kotlin-concurrent` — `createConcurrentStore` (lock-free reads + reentrant-lock-serialized writes; the CallerSerialized strategy).
- `redux-kotlin-granular` — `subscribeTo` / `subscribeFields` field-level subscriptions.
- `redux-kotlin-registry` — `StoreRegistry` / `TypedStoreRegistry` keyed multi-store container.
- `redux-kotlin-multimodel` — `ModelState` typesafe heterogeneous model bag.
- `redux-kotlin-multimodel-granular` — granular subscriptions for `ModelState`.
- `redux-kotlin-compose` — Compose `State<T>` bindings (`fieldState`, `selectorState`, `SelectorStore`; deprecated `StableStore` compatibility wrapper).
- `redux-kotlin-compose-multimodel` — Compose bindings for `ModelState`.
- `redux-kotlin-compose-saveable` — `StateSaver` + `Store<S>.rememberSaveableState` store-anchored snapshot persistence (rotation + process death) via `SaveableStateRegistry`.
<!-- assemble:modules:end -->

More modules exist (routing/bundle/bom/devtools/codegen) and unpublished dev tools
(the unified `rk` CLI — `rk devtools` + `rk snapshot`; built by `redux-kotlin-cli`) → see `docs/agent/api-map.md`.

`examples/` = sample apps; `examples/taskflow` is the canonical app.

## Build / test / lint

- `./gradlew build` — full build (compile + test + detekt + `apiCheck`).
- `./gradlew detektAll` — lint the whole tree.
- `./gradlew apiCheck` — verify public API matches committed dumps (`apiDump` to regenerate).
- `./gradlew :<module>:jvmTest` — one module's JVM tests.

Hard rules: never `--no-verify` (git hooks run `detektAll`); `explicitApi()` is
on, so every `public` declaration needs an explicit modifier AND a KDoc — KDoc
does not auto-correct. Full gate detail → `CLAUDE.md`.

## Design rules

(There are no named Rules A or B.) Faithful one-liners from
`examples/taskflow/ARCHITECTURE.md` §17:

<!-- assemble:rules:start -->
- **Rule C — Render isolation.** No composable reads a model (board/cards/columns) wholesale; every leaf binds the narrowest slice via `selectorState`/`fieldStateOf` and is wrapped in `key(...)`; list derivation lives in pure functions/reducers.
- **Rule D — Identity split.** A profile edit fans `EditProfile` to the root account directory, the per-account `CollaboratorsModel`, and `SessionModel` (bio) — identity is never duplicated inconsistently.
- **Rule E — Off-main effects.** Effects originate only in middleware and run off-main; dispatch marshals back to main via `NotificationContext` (no explicit main hop). Per-feature handlers compose into one `effectsMiddleware`.
- **Rule F — Delta-only status.** `SyncEngine` emits `onStatus` only on a real `SyncStatus` change.
- **Rule G — Mint at the edge.** Ids and timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — Single inset point.** Window insets are applied once at the shell root.
- **Rule I — State-keyed lifecycle effects.** Screen-data loads key on **state** (the nav-derived slice, e.g. `BoardLifecycleEffect` on `nav.activeBoardId`), never on navigation events — state-only entry points (process-death restore, deep links, DevTools time-travel, account-switch hydration) set state without replaying events, so an event-keyed load silently never runs. Fallback: match the hydrating action in middleware.
<!-- assemble:rules:end -->

Full text → `examples/taskflow/ARCHITECTURE.md`.

## Where things live

Recommended app layout is package-by-feature: a `feature/<name>/` slice (model,
actions, reducer, effects, screen, selectors, tests) plus shared `core` (domain
kernel — no Compose/IO), `infra` (platform shims, db, data/sync), `app`
(composition root: store factories, nav), and `ui` (theme, locals, widgets).
Canonical example: `examples/taskflow`.

## Deeper knowledge

- **T1** per-concern guides → `docs/agent/references/` (all eleven live: feature-slice, store-setup, compose-binding, effects-sync, testing, platform-shims, modularization, devtools, store-consistency-model, state-persistence, snapshot — see `docs/agent/references/README.md`).
  - Snapshot / golden UI testing → `docs/agent/references/snapshot.md`.
- Task → guide routing (decision table) → `docs/agent/references/README.md`.
- **T2** → `examples/taskflow/ARCHITECTURE.md` (full architecture + design rules) and `docs/agent/api-map.md` (module → `.api` index).
