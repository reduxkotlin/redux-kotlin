---
id: ecosystem
title: Ecosystem
sidebar_label: Ecosystem
---

# Ecosystem

Redux is a tiny library, but its contracts and APIs are carefully chosen to spawn an ecosystem of
tools and extensions, and the community has created a wide variety of helpful addons, libraries, and
tools. You don't need to use any of these addons to use Redux, but they can help make it easier to
implement features and solve problems in your application.

For inspiration or examples, the JS ecosystem is quite rich and [can be explored
here](https://redux.js.org/introduction/ecosystem).

## First-party modules

The core `redux-kotlin` library is intentionally minimal. Optional
companion modules build on its contracts; add only the ones you need —
or start with a [bundle](../advanced/bundle), the recommended install.
Published modules share the core's group id `org.reduxkotlin` and version;
rows marked as repo tools are developer tools that ship in the repository
and are not published to Maven.

### Bundles — the recommended starting point

| Module | Purpose |
|---|---|
| `redux-kotlin-bundle-compose` | [The bundle](../advanced/bundle) plus the Compose bindings and `redux-kotlin-compose-saveable` — the single dependency for Compose Multiplatform apps. |
| `redux-kotlin-bundle` | [One-dependency assembly](../advanced/bundle) of the concurrent `ModelState` stack (`createConcurrentModelStore`, registry helpers) — concurrent + multimodel + granular + routing in one artifact, no Compose runtime. |
| `redux-kotlin-bom` | [Maven BOM](../advanced/bundle#aligning-versions-the-bom) aligning the versions of every `org.reduxkotlin` module, for à-la-carte setups. |

### Core & stores

| Module | Purpose |
|---|---|
| `redux-kotlin` | The core store, reducer, and middleware contracts. |
| `redux-kotlin-concurrent` | [`createConcurrentStore`](../advanced/concurrent-store) — lock-free reads with caller-serialized writes; `NotificationContext` controls where subscriber callbacks run (incl. `coalescingNotificationContext` for lag-free main-thread notify). |
| `redux-kotlin-threadsafe` | **Deprecated** in favor of `redux-kotlin-concurrent`. [`createThreadSafeStore`](../api/createthreadsafestore) — a store wrapper that serialises every store function on one lock. |
| `redux-kotlin-thunk` | [Thunk middleware](../advanced/async-actions) for async actions — dispatch functions that can themselves dispatch once async work completes (`createThunkMiddleware()`). |

### State shape

| Module | Purpose |
|---|---|
| `redux-kotlin-granular` | [Granular Subscriptions](../advanced/granular-subscriptions) — subscribe to a single field or selector with an `(old, new)` callback that fires only when that value changes. |
| `redux-kotlin-registry` | [Store Registry](../advanced/store-registry) — manage many stores keyed by a unique identifier, with thread-safe `getOrCreate` and manual lifecycle. |
| `redux-kotlin-multimodel` | [`ModelState`](../advanced/multimodel) — a type-safe bag of independent feature models keyed by class, plus `combineModelReducers` to drive them from one store. |
| `redux-kotlin-multimodel-granular` | [Granular subscriptions for `ModelState`](../advanced/multimodel#granular-subscriptions-redux-kotlin-multimodel-granular) — `subscribeTo(Model::field)` / `subscribeToModel(...)`. |

### Routing

| Module | Purpose |
|---|---|
| `redux-kotlin-routing` | [Routed `(model, action)` dispatch](../advanced/routing) over `ModelState` — declare each slot with `model(initial) { on<Action> { … } }` instead of a `when(action)` cascade; optional `preloadedState` overlays restored models at construction. |
| `redux-kotlin-routing-codegen` | [KSP processor](../advanced/routing#code-generation-reduce--reduxinitial) for the routing DSL — `@Reduce` / `@ReduxInitial` annotations generate the `ReduxModule` wiring. Repo tool: not yet published to Maven Central. |

### Compose

| Module | Purpose |
|---|---|
| `redux-kotlin-compose` | [Compose integration](../advanced/compose-integration) — bind store fields to Compose `State<T>` with `fieldState` / `selectorState`. |
| `redux-kotlin-compose-multimodel` | Compose [`fieldState(Model::field)` bindings](../advanced/multimodel#compose-bindings-redux-kotlin-compose-multimodel) for `ModelState` stores. |
| `redux-kotlin-compose-saveable` | [`StateSaver` + `rememberSaveableState`](../advanced/compose-integration#saving-state-across-rotation--process-death) store-anchored snapshot persistence for Compose (survives rotation + process death) via Compose `SaveableStateRegistry`. |

### DevTools (experimental)

| Module | Purpose |
|---|---|
| `redux-kotlin-devtools-*` | [DevTools family](../advanced/devtools) — action/state inspection, diffing, and pipeline timing for a running app. Published (experimental — exempt from semver until the surface stabilizes): `core`, `bridge`, `remote`, `inapp`, `inapp-noop` (release no-op facade), `ui`. Unpublished repo tools: `standalone` (Compose desktop monitor app) and `cli` (library behind `rk devtools`). |
| `redux-kotlin-snapshot` | [Snapshot testing](../advanced/snapshot-testing) — headlessly render Compose Multiplatform screens from redux-kotlin state to PNG (`f(state) -> UI`), with golden-image diffing and an HTML dashboard. Accessed via `rk snapshot` (from `redux-kotlin-cli`). Unpublished repo tool. |
| `redux-kotlin-cli` | Produces the unified `rk` binary — `rk devtools` (bridge receiver + capture queries) and `rk snapshot` (headless Compose renderer). Install from source: `./gradlew :redux-kotlin-cli:installDist` (binary: `redux-kotlin-cli/build/install/rk/bin/rk`; needs JDK 17+). Or install via brew/scoop (see `redux-kotlin-cli-dist`). Unpublished repo tool. |
| `redux-kotlin-cli-dist` | Compose bundled-JRE packaging for `rk` — produces per-OS app-images via `createDistributable` and archives via `packageRkArchive`; published to the Homebrew tap and Scoop bucket on a tagged release. End users install via `brew install reduxkotlin/tap/rk` / `scoop install rk` (no Java required). Unpublished repo tool. |

## Community

**[Presenter-middleware](https://github.com/reduxkotlin/presenter-middleware)** *(archived)*  
A middleware for writing concise UI binding code and no-fuss lifecycle/subscription management.
The repository is archived and no longer maintained.
