---
tier: T1
concern: modularization
derives_from:
  - settings.gradle.kts → published module list
  - examples/taskflow/composeApp/build.gradle.kts → redux-kotlin-bundle-compose
  - redux-kotlin-bundle-compose/build.gradle.kts → redux-kotlin-bundle, redux-kotlin-compose-multimodel
  - redux-kotlin-bundle/build.gradle.kts → redux-kotlin-concurrent, redux-kotlin-registry, redux-kotlin-routing, redux-kotlin-multimodel-granular
api_files:
  - redux-kotlin/api/redux-kotlin.klib.api
  - redux-kotlin-concurrent/api/redux-kotlin-concurrent.klib.api
  - redux-kotlin-threadsafe/api/redux-kotlin-threadsafe.klib.api
  - redux-kotlin-registry/api/redux-kotlin-registry.klib.api
  - redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api
  - redux-kotlin-multimodel-granular/api/redux-kotlin-multimodel-granular.klib.api
  - redux-kotlin-granular/api/redux-kotlin-granular.klib.api
  - redux-kotlin-compose/api/redux-kotlin-compose.klib.api
  - redux-kotlin-compose-multimodel/api/redux-kotlin-compose-multimodel.klib.api
  - redux-kotlin-bundle/api/redux-kotlin-bundle.klib.api
  - redux-kotlin-bundle-compose/api/redux-kotlin-bundle-compose.klib.api
rules: []
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 3c1cd67, date: 2026-06-04 }
---

# Modularization

> Which redux-kotlin library module to depend on for which job, and the package-by-feature dependency
> direction inside an app module.

## Library module selection

The core is one module; everything else layers onto the same `Store<S>` contract. Pick the smallest set
that does the job. Full per-module API lives in the `api_files` above.

| Module | Use it when |
|---|---|
| `:redux-kotlin` | always — the `Store`/`Reducer`/`Middleware` contract, `createStore`, `applyMiddleware`, `compose`. |
| `:redux-kotlin-concurrent` | the store is touched from multiple threads and you want lock-free reads + serialized writes (the production default; taskflow uses this via the bundle). |
| `:redux-kotlin-threadsafe` | you want a simpler atomicfu-locked wrapper instead of the concurrent strategy. |
| `:redux-kotlin-granular` | field-level subscriptions (`subscribeTo` / `subscribeFields`) to avoid waking subscribers on unrelated changes. |
| `:redux-kotlin-registry` | a dynamic, keyed set of stores with one lifecycle each (taskflow's per-account stores). |
| `:redux-kotlin-multimodel` | one store holds several unrelated model types in a typesafe `ModelState` bag. |
| `:redux-kotlin-multimodel-granular` | granular subscriptions over a `ModelState` bag. |
| `:redux-kotlin-compose` | Compose `State<T>` bindings (`fieldState`, `selectorState`, `StableStore`) for a single-model store. |
| `:redux-kotlin-compose-multimodel` | Compose bindings for a `ModelState` bag (`fieldStateOf`). |

### Decision rules

- **Threading:** single-threaded → bare `:redux-kotlin`; multi-threaded → `:redux-kotlin-concurrent`
  (preferred) or `:redux-kotlin-threadsafe`.
- **One store vs many:** several unrelated models in one store → `:redux-kotlin-multimodel`; a dynamic
  keyed family of stores → `:redux-kotlin-registry`. (When to actually split → [store-setup.md](./store-setup.md).)
- **Subscriptions:** add `:redux-kotlin-granular` / `:redux-kotlin-multimodel-granular` only when
  whole-store notification is a measured cost; Compose's `fieldStateOf`/`selectorState` already isolate
  renders ([compose-binding.md](./compose-binding.md)).
- **Compose:** match the binding module to the store shape — `compose` for single-model, `compose-multimodel`
  for a `ModelState` bag.

## The bundles

Two aggregate modules save you wiring the common stacks; depend on one instead of six:

- `examples/taskflow/composeApp/build.gradle.kts → redux-kotlin-bundle-compose` is taskflow's single
  redux dependency.
- It re-exports (`api`) `redux-kotlin-bundle-compose/build.gradle.kts → redux-kotlin-bundle, redux-kotlin-compose-multimodel`,
  and the bundle in turn re-exports
  `redux-kotlin-bundle/build.gradle.kts → redux-kotlin-concurrent, redux-kotlin-registry, redux-kotlin-routing, redux-kotlin-multimodel-granular`.

So a Compose KMP app gets the concurrent store + registry + routing DSL + multimodel + Compose bindings
from one line. Drop to individual modules only under a size/minimal-deps constraint.

## App-internal modularization — package-by-feature

Inside an app module, organize by feature, not by layer (the recommended default — see the strategy
spec's §5.1). taskflow's `commonMain` is: `feature/<name>/` slices over a shared `core` kernel, with
`infra`, `app`, and `ui` as the shared homes. The dependency direction is one-way:

```
core  ←  infra
core / infra / ui  ←  feature/*
everything  ←  app
```

`core` never imports a `feature`. A `feature` may use `core`/`infra`/`ui` and may reference another
feature's public leaf actions, but features do not reach into each other's internals. This keeps a
feature's files context-local (fewer files for an agent to load) and the kernel reusable. Full element
breakdown → [feature-slice.md](./feature-slice.md).

When an app grows large enough that features need separate build units, the same direction maps onto
Gradle modules (`:feature:board` depending on `:core`), but the package-level split is the first step and
is usually sufficient.

## Verify loop

`./gradlew build` resolves the dependency graph and runs `apiCheck` for every library module; a wrong or
missing module dependency fails compilation. `./gradlew apiDump` regenerates the `api_files` after an
intentional public-API change.

## See also

- [store-setup.md](./store-setup.md) — the factories these modules supply.
- [feature-slice.md](./feature-slice.md) — the package-by-feature layout in detail.
- [README](./README.md)
