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

The nine published core modules (each "use for X"):

- `redux-kotlin` — core contract: `Store`/`TypedStore`, `Reducer`, `Middleware`, `createStore`, `applyMiddleware`, `combineReducers`, `compose`.
- `redux-kotlin-threadsafe` — `createThreadSafeStore` (atomicfu-locked store wrapper).
- `redux-kotlin-concurrent` — `createConcurrentStore` (lock-free reads + reentrant-lock-serialized writes).
- `redux-kotlin-granular` — `subscribeTo` / `subscribeFields` field-level subscriptions.
- `redux-kotlin-registry` — `StoreRegistry<K,S>` / `TypedStoreRegistry` keyed multi-store container.
- `redux-kotlin-multimodel` — `ModelState` typesafe heterogeneous model bag.
- `redux-kotlin-multimodel-granular` — granular subscriptions for `ModelState`.
- `redux-kotlin-compose` — Compose `State<T>` bindings (`fieldState`, `selectorState`, `StableStore`).
- `redux-kotlin-compose-multimodel` — Compose bindings for `ModelState`.

More modules exist (routing/bundle/bom/devtools/codegen) → see `docs/agent/api-map.md`.

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

- **Rule C — render isolation.** No composable reads a slot wholesale; each leaf binds the narrowest slice via `selectorState`/`fieldStateOf`; list derivation lives in pure functions/reducers.
- **Rule D — identity split.** A profile edit fans to the root account directory, the per-account model, and the session model so identity is never duplicated inconsistently.
- **Rule E — off-main effects.** All repository/sync work runs off-main; dispatch marshals notifications back to main via `NotificationContext` (no explicit main hop in effects).
- **Rule F — delta-only status.** `SyncEngine` emits `onStatus` only on a real `SyncStatus` change.
- **Rule G — mint at the edge.** Ids and timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — single inset point.** Window insets are applied once at the shell root.

Full text → `examples/taskflow/ARCHITECTURE.md`.

## Where things live

Recommended app layout is package-by-feature: a `feature/<name>/` slice (model,
actions, reducer, effects, screen, selectors, tests) plus shared `core` (domain
kernel — no Compose/IO), `infra` (platform shims, db, data/sync), `app`
(composition root: store factories, nav), and `ui` (theme, locals, widgets).
Canonical example: `examples/taskflow`.

## Deeper knowledge

- **T1** per-concern guides → `docs/agent/references/` (`feature-slice.md` is live; the other six are planned — see `docs/agent/references/README.md`).
- **T2** → `examples/taskflow/ARCHITECTURE.md` (full architecture + design rules) and `docs/agent/api-map.md` (module → `.api` index).
