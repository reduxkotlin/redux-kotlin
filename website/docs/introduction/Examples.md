---
id: examples
title: Examples
sidebar_label: Examples
---

# Examples

ReduxKotlin includes a few examples currently and plans to port many of the JS Redux examples over.
You may also find the [JS examples](https://redux.js.org/introduction/examples) helpful

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

Run the [Counter](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/counter) example:



```sh
git clone https://github.com/reduxkotlin/redux-kotlin.git

./gradlew examples:counter:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

## Todos

Run the [Todos](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/todos) example:

```sh
git clone https://github.com/reduxkotlin/redux-kotlin.git

./gradlew examples:todos:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

## NameGame

This is a multiplatform app for Android and iOS. It is a quiz on dog/cat breeds. This is a more
complete example of how to use Redux in a real application. Async actions such as network requests
and delays are demonstrated. Also used is the
[presenter-middleware](https://github.com/reduxkotlin/presenter-middleware) as a presentation layer.

Run the [NameGame](https://github.com/reduxkotlin/NameGameSampleApp) example:

```sh
git clone https://github.com/reduxkotlin/NameGameSampleApp.git

./gradlew android:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

iOS:

Open the iOS/NameGame in XCode.


## ReadingList 

This is a multiplatform app for Android and iOS. It is a quiz on dog/cat breeds. This is a more
complete example of how to use Redux in a real application. Async actions such as network requests
and delays are demonstrated. Also used is the
[presenter-middleware](https://github.com/reduxkotlin/presenter-middleware) as a presentation layer.

Run the [ReadingList](https://github.com/reduxkotlin/ReadingListSampleApp) example:


```sh
git clone https://github.com/reduxkotlin/ReadingListSampleApp.git

./gradlew android:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

iOS:

Open the iOS/ReadingList in XCode.


## MovieSwiftUI-Kotlin

Fork of an 100% SwiftUI app that uses shared Kotlin code for networking, preferences, reducers,
actions, and more.

Run the [MovieSwiftUI-Kotlin](https://github.com/reduxkotlin/MovieSwiftUI-Kotlin) example:

```sh
git clone https://github.com/reduxkotlin/MovieSwiftUI-Kotlin.git
```

Open the iOS/ReadingList in XCode.

A minimal Jetpack Compose screen is implemented.

