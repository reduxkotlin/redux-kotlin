---
id: examples
title: Examples
sidebar_label: Examples
---

# Examples

The example apps live in the repository's
[`examples/`](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples)
directory, so they always build against the current source. You may also find
the [JS examples](https://redux.js.org/introduction/examples) helpful for the
general patterns.

## TaskFlow — the reference architecture

[TaskFlow](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/taskflow)
is a Compose Multiplatform Kanban app (Android / iOS / desktop / wasm) built to
exercise the `redux-kotlin-bundle-compose` stack end-to-end, and the best place
to see the patterns from these docs in one codebase:

- a root store plus a store-per-account registry (`createConcurrentModelStore` + `StoreRegistry`)
- `ModelState` multi-model slots wired with the routing DSL (`model(initial) { on<Action> { … } }`)
- granular Compose bindings (`fieldStateOf` / `selectorState`) tuned for tight render isolation
- offline-first persistence with a sync engine and per-op inverse revert
- UI-state persistence across rotation & process death via
  [`redux-kotlin-compose-saveable`](../advanced/compose-integration#saving-state-across-rotation--process-death)

Its [ARCHITECTURE.md](https://github.com/reduxkotlin/redux-kotlin/blob/master/examples/taskflow/ARCHITECTURE.md)
documents every design rule the app follows.

```sh
git clone https://github.com/reduxkotlin/redux-kotlin.git
./gradlew :examples:taskflow:composeApp:run        # desktop
./gradlew :examples:taskflow:androidApp:installDebug
```

## Counter

The smallest end-to-end app: a multiplatform common module (reducer + state)
with an Android view-binding UI, granular field subscriptions, and a
debug-only DevTools enhancer.

Run the [Counter](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/counter) example:

```sh
git clone https://github.com/reduxkotlin/redux-kotlin.git

./gradlew :examples:counter:android:installDebug
```

or open the root project in Android Studio/IntelliJ and run the
`examples/counter` Android configuration.

## Todos

The classic Redux todos app: actions, a composed root reducer, and a visibility
filter, with an Android RecyclerView UI.

Run the [Todos](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/todos) example:

```sh
git clone https://github.com/reduxkotlin/redux-kotlin.git

./gradlew :examples:todos:android:installDebug
```

or open the root project in Android Studio/IntelliJ and run the
`examples/todos` Android configuration.

## Older external samples

Earlier standalone sample repositories on the
[reduxkotlin GitHub organization](https://github.com/reduxkotlin) —
NameGameSampleApp, ReadingListSampleApp, MovieSwiftUI-Kotlin — are **archived**
and no longer build against current releases. They remain readable for
historical reference, but start from TaskFlow or the in-repo examples above.
