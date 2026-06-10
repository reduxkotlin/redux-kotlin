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
companion modules build on its contracts; add only the ones you need.
All share the core's group id `org.reduxkotlin` and version.

| Module | Purpose |
|---|---|
| `redux-kotlin` | The core store, reducer, and middleware contracts. |
| `redux-kotlin-threadsafe` | [`createThreadSafeStore`](../api/createthreadsafestore) — a store wrapper that serialises access so any thread can dispatch, read, and subscribe safely. |
| `redux-kotlin-granular` | [Granular Subscriptions](../advanced/granular-subscriptions) — subscribe to a single field or selector with an `(old, new)` callback that fires only when that value changes. |
| `redux-kotlin-registry` | [Store Registry](../advanced/store-registry) — manage many stores keyed by a unique identifier, with thread-safe `getOrCreate` and manual lifecycle. |
| `redux-kotlin-multimodel` | `ModelState` — a type-safe bag of independent feature models keyed by class, plus `combineModelReducers` to drive them from one store. |
| `redux-kotlin-multimodel-granular` | [Granular subscriptions for `ModelState`](../advanced/granular-subscriptions#multi-model-stores) — `subscribeTo(Model::field)` / `subscribeToModel(...)`. |
| `redux-kotlin-compose` | [Compose integration](../advanced/compose-integration) — bind store fields to Compose `State<T>` with `fieldState` / `selectorState`. |
| `redux-kotlin-compose-multimodel` | Compose `fieldState(Model::field)` bindings for `ModelState` stores. |
| `redux-kotlin-compose-saveable` | [`StateSaver` + `rememberSaveableState`](../advanced/compose-integration#saving-state-across-rotation--process-death) store-anchored snapshot persistence for Compose (survives rotation + process death) via Compose `SaveableStateRegistry`. |
| `redux-kotlin-concurrent` | `createConcurrentStore` — lock-free reads with caller-serialized writes; `NotificationContext` controls where subscriber callbacks run (incl. `coalescingNotificationContext` for lag-free main-thread notify). |
| `redux-kotlin-routing` | Routed `(model, action)` dispatch over `ModelState` — declare each slot with `model(initial) { on<Action> { … } }` instead of a `when(action)` cascade; optional `preloadedState` overlays restored models at construction. |
| `redux-kotlin-routing-codegen` | KSP processor for the routing DSL — `@Reduce` / `@ReduxInitial` annotations generate the `ReduxModule` wiring. |
| `redux-kotlin-bundle` | One-dependency assembly of the concurrent `ModelState` stack (`createConcurrentModelStore`, registry helpers) — concurrent + multimodel + granular + routing in one artifact. |
| `redux-kotlin-bundle-compose` | The bundle plus the Compose bindings and `redux-kotlin-compose-saveable` — the single dependency for Compose Multiplatform apps. |
| `redux-kotlin-bom` | Maven BOM aligning the versions of every `org.reduxkotlin` module. |
| `redux-kotlin-devtools-*` | DevTools family (`core`, `bridge`, `remote`, `inapp`, `ui`, `cli`, `standalone`) — action/state inspection and diffing for a running app. |

## Community

**[Presenter-middleware](https://github.com/reduxkotlin/presenter-middleware)**  
A middleware for writing concise UI binding code and no-fuss lifecycle/subscription management.
