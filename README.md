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

__How to add to project:__

Artifacts are hosted on maven central. They are published with gradle metadata, so you may need to
enable
with `enableFeaturePreview("GRADLE_METADATA")` in your settings.gradle file. For multiplatform, add
the following to
your shared module:

```kotlin
kotlin {
    sourceSets {
        commonMain { //   <---  name may vary on your project
            dependencies {
                implementation("org.reduxkotlin:redux-kotlin-threadsafe:_")
            }
        }
    }
}
```

For JVM only:

```kotlin
implementation("org.reduxkotlin:redux-kotlin-threadsafe-jvm:_>")
```

*Non threadsafe store is available. Typical usage will be with the threadsafe
store. [More info read here](https://www.reduxkotlin.org/introduction/getting-started)

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

__Create a synchronized store__

```kotlin
val store =
    createThreadSafeStore(reducer, AppState(user, listOf()), applyMiddleware(loggingMiddleware))
```

Access to `store` methods like `dispatch` and `getState` will be synchronized. Note: if using a
thread safe store with enhancers or middleware that require access to store methods, see usage
below.

__Create a synchronized store using an enhancer__

```kotlin
val store = createStore(
    reducer,
    AppState(user, listOf()),
    compose(
        applyMiddleware(createThunkMiddleware(), loggingMiddleware),
        createSynchronizedStoreEnhancer() // needs to be placed after enhancers that requires synchronized store methods
    )
)
```

Access to `store` methods like `dispatch` and `getState` will be synchronized, and enhancers (
eg. `applyMiddleware`) that are placed above `createSynchronizedStoreEnhancer` in the enhancer
composition chain will receive the synchronized store.

## Extensions

Here's a list of optional extensions available. Raise an issue to add yours!

- [redux-kotlin-thunk](https://github.com/reduxkotlin/redux-kotlin-thunk)
- [redux-kotlin-compose](https://github.com/reduxkotlin/redux-kotlin-compose)
- [presenter-middleware](https://github.com/reduxkotlin/presenter-middleware)

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
