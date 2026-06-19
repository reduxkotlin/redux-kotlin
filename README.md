# [Redux-Kotlin](https://reduxkotlin.org)

[![Release](https://github.com/reduxkotlin/redux-kotlin/actions/workflows/release.yml/badge.svg)](https://github.com/reduxkotlin/redux-kotlin/actions/workflows/release.yml)
![badge][badge-android]
![badge][badge-ios]
![badge][badge-native]
![badge][badge-js]
![badge][badge-jvm]
![badge][badge-linux]
![badge][badge-windows]
![badge][badge-mac]
[![Slack chat](https://img.shields.io/badge/kotlinlang-%23redux-green?logo=slack&style=flat-square)][slack]
[![Dokka docs](https://img.shields.io/badge/docs-dokka-orange?style=flat-square&logo=kotlin)](http://reduxkotlin.github.io/redux-kotlin)
[![Version maven-central](https://img.shields.io/maven-central/v/org.reduxkotlin/redux-kotlin?logo=apache-maven&style=flat-square)](https://mvnrepository.com/artifact/org.reduxkotlin/redux-kotlin/latest)
[![Version maven-snapshot](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Foss.sonatype.org%2Fcontent%2Frepositories%2Fsnapshots%2Forg%2Freduxkotlin%2Fredux-kotlin%2Fmaven-metadata.xml&logo=apache-maven&label=maven-snapshot&style=flat-square)](https://oss.sonatype.org/content/repositories/snapshots/org/reduxkotlin/redux-kotlin/)

A redux standard for Kotlin that supports multiplatform projects.

Full documentation at http://reduxkotlin.org.

## Mission Statement

Provide a standard redux implementation for Kotlin. In doing so will foster a ecosystem of
middleware, store
enhancers, & dev tools. These core values will guide descisions for the project:

* core redux-kotlin will be a minimal implementation that other libraries can build upon
* modular development (follow example of https://github.com/reduxjs)
* support for all platforms supported by Kotlin multiplatform (JVM, iOS, Native, JS, WASM)
* developed in open and enable discussion for all interested parties via open channels (slack,
  github, etc. TBD)
* not owned by a individual or company

Redux in Kotlin, and in mobile in particular, may differ a bit from javascript. Many have found the
basic pattern useful
on Android & iOS leading to tens of opensource redux libraries in Kotlin, Java, and Swift, yet an
ecosystem has yet to
emerge. A port of javascript redux is a good starting point for creating a standard and will aid in
cross-pollination of
middleware, store enhancers, & dev tools from the javascript world.

Redux has proven helpful for state management in mobile. A multiplatform Kotlin implementation &
ecosystem will increase
developer productivity and code reuse across platforms.

[Droidcon NYC Slides](https://www.slideshare.net/PatrickJackson14/reduxkotlinorg-droidcon-nyc-2019)
Video TBA

## *** PLEASE FILL OUT THE [Redux on Mobile Survey](https://docs.google.com/forms/d/e/1FAIpQLScEQ9zGndU48AUeGKR6PPE13IqhIFmTL570wDodQUEilhwMzw/viewform?usp=sf_link) ***

## How to add to project

Artifacts are hosted on Maven Central. Replace `<version>` with the latest release shown by the
badge above.

Requirements:

- Kotlin 1.9 or newer (the published artefacts are built with Kotlin 2.3)
- JVM/Android consumers: JDK 17+ on Android; library bytecode is JVM 17
- Android: `minSdk 21` or higher
- Supported KMP targets: `jvm`, `js` (browser/node), `android`, `iosArm64`, `iosX64`,
  `iosSimulatorArm64`, `macosArm64`, `macosX64`, `linuxArm64`, `linuxX64`, `mingwX64`

### Recommended: the bundles

One dependency for the common redux-kotlin stack. For
**Jetpack / Multiplatform Compose apps**:

```kotlin
kotlin {
    sourceSets {
        commonMain { //   <---  name may vary on your project
            dependencies {
                implementation("org.reduxkotlin:redux-kotlin-bundle-compose:<version>")
            }
        }
    }
}
```

For **everything else** (no Compose runtime pulled in):

```kotlin
implementation("org.reduxkotlin:redux-kotlin-bundle:<version>")
```

`redux-kotlin-bundle` transitively brings the core, the concurrent store
(lock-free reads + serialized writes), granular field-level subscriptions,
`ModelState` multi-model state (plus granular subscriptions over it), the
multi-store registry, and the routed-reducer DSL. `redux-kotlin-bundle-compose`
adds the Compose `State<T>` bindings and saveable snapshot persistence on top.

```kotlin
val store = createConcurrentModelStore {
    model(UserModel()) {
        on<LoggedIn>  { s, a -> s.copy(user = a.user) }
        on<LoggedOut> { s, _ -> s.copy(user = null) }
    }
}
store.dispatch(LoggedIn("ann"))
```

See the [Bundle guide](https://www.reduxkotlin.org/advanced/bundle) and the
[TaskFlow sample](examples/taskflow) — a Compose Multiplatform Kanban app built
on `redux-kotlin-bundle-compose` end-to-end.

### À-la-carte

Every module is published individually. Import the BOM once to keep versions
aligned, then add only what you need without repeating versions:

```kotlin
dependencies {
    implementation(platform("org.reduxkotlin:redux-kotlin-bom:<version>"))
    implementation("org.reduxkotlin:redux-kotlin")
    implementation("org.reduxkotlin:redux-kotlin-concurrent")
}
```

| Group | Modules |
|---|---|
| Core & stores | `redux-kotlin` (core contracts + `createStore`), `redux-kotlin-concurrent` (`createConcurrentStore` — the recommended thread-safe store), `redux-kotlin-threadsafe` (**deprecated** — fully-synchronized predecessor), `redux-kotlin-thunk` (async actions middleware) |
| State shape | `redux-kotlin-granular` (field-level subscriptions), `redux-kotlin-registry` (keyed multi-store container), `redux-kotlin-multimodel` (`ModelState` typesafe model bag), `redux-kotlin-multimodel-granular` (granular subscriptions over `ModelState`) |
| Compose | `redux-kotlin-compose` (`fieldState` / `selectorState` bindings), `redux-kotlin-compose-multimodel` (bindings for `ModelState`), `redux-kotlin-compose-saveable` (snapshot persistence across rotation + process death) |
| Routing | `redux-kotlin-routing` (routed `(model, action)` reducer DSL), `redux-kotlin-routing-codegen` (KSP `@Reduce` processor — in-repo, not yet on Maven Central) |
| Bundles | `redux-kotlin-bundle`, `redux-kotlin-bundle-compose`, `redux-kotlin-bom` (Maven BOM) |
| DevTools (experimental) | `redux-kotlin-devtools-core`, `-bridge`, `-remote`, `-inapp`, `-inapp-noop`, `-ui` — aligned by the BOM but exempt from semver until the surface stabilizes |
| Dev tools (in-repo, unpublished) | `redux-kotlin-cli` — the unified `rk` binary (`rk devtools` + `rk snapshot`); `redux-kotlin-devtools-standalone` |

## Core API

Usage is very similar to JS Redux and those docs will be useful https://redux.js.org/. These docs
are not an intro to
Redux, and just documentation on Kotlin specific bits. For more info on Redux in general, check
out https://redux.js.org/.

__Create an AppState class__

```kotlin
data class AppState(val user: User, val feed: List<Feed>)
```

__Create Reducers:__

```kotlin
val reducer: Reducer<AppState> = { state, action ->
    when (action) {
        is UserLoggedInAction -> state.copy(user = action.user)
            ...
    }
}
```

__Create Middleware:__
There are a few ways to create middleware:

Using a curried function stored in a val/var:

```kotlin
val loggingMiddleware: Middleware = { store ->
    { next ->
        { action ->
            //log here
            next(action)
        }
    }
}
```

Using a function:

```kotlin
fun loggingMiddleware(store: Store) = { next: Dispatcher ->
    { action: Any ->
        //log here
        next(action)
    }
}
```

Using the convenience helper function `middleware`:

```kotlin
val loggingMiddleware = middleware { store, next, action ->
    //log here
    next(action)
}
```

__Create a store__

```kotlin
val store = createStore(reducer, AppState(user, listOf()), applyMiddleware(loggingMiddleware))
```

You then will have access to dispatch and subscribe functions from the `store`.

For thread-safe access from multiple threads, use `createConcurrentStore` from
`redux-kotlin-concurrent` (lock-free reads, serialized writes) — the older
`createThreadSafeStore` / `createSynchronizedStoreEnhancer` are deprecated; see the
[threading docs](https://www.reduxkotlin.org/introduction/threading).

## DevTools

The repo ships a DevTools family for inspecting a running redux-kotlin app:

- **In-app drawer** (`redux-kotlin-devtools-inapp`) — a Compose Multiplatform overlay with
  Actions / State / Diff / Pipeline / Outputs tabs, swapped out at release time by
  `redux-kotlin-devtools-inapp-noop`.
- **Standalone desktop monitor** (`redux-kotlin-devtools-standalone`) — observe any app
  (including headless/native) from outside its process via the WebSocket bridge.
- **CLI** (`redux-kotlin-cli`) — the `rk` unified terminal tool: `rk devtools` captures, queries,
  diffs, and tails action streams; `rk snapshot` renders Compose screens headlessly.
- **Remote streaming** (`redux-kotlin-devtools-remote`) — stream to the Redux DevTools
  browser extension.

See the [integration guide](docs/devtools.md) and the
[website DevTools page](https://www.reduxkotlin.org/advanced/devtools).

For *visual* verification, `rk snapshot` (the snapshot subcommand of the unified `rk` CLI)
headlessly renders Compose screens from state to PNG with golden-image diffing —
`f(state) -> UI`. See the [snapshot module README](redux-kotlin-snapshot/README.md) and the
[snapshot testing guide](https://www.reduxkotlin.org/advanced/snapshot-testing).

## Extensions

Optional companion modules in this repo build on the core contracts — Compose bindings
(`redux-kotlin-compose`), async actions (`redux-kotlin-thunk`), granular subscriptions,
multi-store registries, routing, and the one-dependency bundles. See the
[ecosystem page](https://www.reduxkotlin.org/introduction/ecosystem) for the full list.
Raise an issue to add your external extension!

## Communication

Want to give feedback, contribute, or ask questions?

- Chat on [#redux][slack] slack channel
- Use [Trello boards](https://trello.com/reduxkotlinorg)
- Raise GitHub [issues](https://github.com/reduxkotlin/redux-kotlin/issues)
- Ask questions on
  GitHub [discussions](https://github.com/reduxkotlin/redux-kotlin/discussions/categories/q-a)

[badge-android]: http://img.shields.io/badge/platform-android-brightgreen.svg?style=flat

[badge-ios]: http://img.shields.io/badge/platform-ios-brightgreen.svg?style=flat

[badge-native]: http://img.shields.io/badge/platform-native-lightgrey.svg?style=flat

[badge-js]: http://img.shields.io/badge/platform-js-yellow.svg?style=flat

[badge-jvm]: http://img.shields.io/badge/platform-jvm-orange.svg?style=flat

[badge-linux]: http://img.shields.io/badge/platform-linux-important.svg?style=flat

[badge-windows]: http://img.shields.io/badge/platform-windows-informational.svg?style=flat

[badge-mac]: http://img.shields.io/badge/platform-macos-lightgrey.svg?style=flat

[slack]: https://kotlinlang.slack.com/archives/C8A8G5F9Q
