---
id: getting-started
title: Getting Started with Redux
sidebar_label: Getting Started with Redux
---

# Getting Started with ReduxKotlin

ReduxKotlin is a predictable state container for Kotlin apps that supports all platforms that Kotlin
can target (JVM, Native, JS, WASM).

ReduxKotlin is a port of [Redux for Javascript](https://redux.js.org) that aims to become a standard
implementation of Redux for Kotlin. Much of the information on [redux.js.org](https://redux.js.org)
applies to ReduxKotlin, however, there is also a lot of Javascript specific information that may
_not_ apply to ReduxKotlin. This site documents how to get started with ReduxKotlin and provides 
examples of using ReduxKotlin in projects. There are suggestions and examples, but ultimately the 
core ReduxLibrary is not opinionated. How you use it in your project is up to you.

## Installation

ReduxKotlin is published to Maven Central. Replace `<version>` with the latest
release.

__Requirements__

- Kotlin 1.9 or newer (artefacts are built with Kotlin 2.3)
- Android `minSdk 21` or higher; library bytecode is JVM 17
- Supported KMP targets: `jvm`, `js` (browser/node), `wasmJs`, `android`, `iosArm64`,
  `iosSimulatorArm64`, `macosArm64`, `linuxArm64` (core), `linuxX64`, `mingwX64`

### Quick start — the bundles (recommended)

One dependency for the common redux-kotlin stack. For **Jetpack / Multiplatform
Compose apps**, declare it in your common sourceset — Gradle metadata picks up
the platform-specific artefacts automatically:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("org.reduxkotlin:redux-kotlin-bundle-compose:<version>")
            }
        }
    }
}
```

For apps **without Compose** (no Compose runtime pulled in):

```kotlin
implementation("org.reduxkotlin:redux-kotlin-bundle:<version>")
```

The bundle brings the core, the concurrent store (lock-free reads + serialized
writes), granular field-level subscriptions, `ModelState` multi-model state,
the multi-store registry, and the routed-reducer DSL; the Compose bundle adds
`State<T>` bindings plus saveable snapshot persistence. Create a store with the
routing DSL:

```kotlin
val store = createConcurrentModelStore {
    model(UserModel()) {
        on<LoggedIn>  { s, a -> s.copy(user = a.user) }
        on<LoggedOut> { s, _ -> s.copy(user = null) }
    }
}
store.dispatch(LoggedIn("ann"))
```

See the [Bundle guide](/advanced/bundle) for the full tour.

### See it in a real app — TaskFlow

[TaskFlow](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/taskflow)
is a Compose Multiplatform Kanban app (Android / iOS / desktop / wasm) built on
`redux-kotlin-bundle-compose` end-to-end — multi-model state, routing, granular
Compose bindings, offline-first persistence. Its
[ARCHITECTURE.md](https://github.com/reduxkotlin/redux-kotlin/blob/master/examples/taskflow/ARCHITECTURE.md)
documents every design rule it follows. More on the [Examples page](/introduction/examples).

### À-la-carte with the BOM

Every module is also published individually. Import the
[BOM](/advanced/bundle#aligning-versions-the-bom) once to align versions, then
add only what you need:

```kotlin
dependencies {
    implementation(platform("org.reduxkotlin:redux-kotlin-bom:<version>"))
    implementation("org.reduxkotlin:redux-kotlin")
    implementation("org.reduxkotlin:redux-kotlin-concurrent")
}
```

The [Ecosystem page](/introduction/ecosystem) lists every module and what it
does.

### Plain core — for library authors and minimal apps

The core `redux-kotlin` artifact is a deliberately minimal implementation of the
Redux contract (`Store`, `Reducer`, `Middleware`, `createStore`) with no extra
dependencies — the right target for middleware/enhancer libraries, JS-only
projects, or apps that want to assemble their own stack:

```kotlin
implementation("org.reduxkotlin:redux-kotlin:<version>")
```

The plain `createStore` is **not** thread-safe. On multi-threaded platforms use
`createConcurrentStore` from `org.reduxkotlin:redux-kotlin-concurrent` (which
the bundles use under the hood).
[**More info on threading available here.**](/introduction/threading)

## Basic Example

The whole state of your app is stored in an object tree inside a single _store_.  
The only way to change the state tree is to emit an _action_, an object describing what happened.  
To specify how the actions transform the state tree, you write pure _reducers_.

That's it!

```kotlin
/**
 * This is a reducer, a pure function with (state, action) -> state signature.
 * It describes how an action transforms the state into the next state.
 *
 * The shape of the state is up to you: it can be a primitive, an array, an object, etc.
 * Usually this will be a data class, because the copy method is useful for creating the new state.
 * In this contrived example, we are just using an Int for the state.
 *
 * In this example, we use a `when` statement and type checking, but other methods are possible,
 * such as a 'type' string field, or delegating the reduction to a method on the action objects.
 */
fun reducer(state: Int, action: Action) =
    when (action) {
        is Increment -> state + 1
        is Decrement -> state - 1
        else -> state
    }

/**
 * Actions are plain objects that represent an action in the app. These can be 
 * plain objects or data classes and have fields that hold data necessary for
 * the reducer to update the state.
 */
class Increment
class Decrement

// Create a Redux store holding the state of your app.
// 0 is the initial state
val store = createConcurrentStore(reducer, 0)

// You can use subscribe() to update the UI in response to state changes.
// Normally you'd use an additional layer or view binding library rather than subscribe() directly.

val unsubscribe = store.subscribe { logger.debug(store.state)}

// The only way to mutate the internal state is to dispatch an action.
// The actions can be serialized, logged or stored.
store.dispatch(Increment())
// Current State: 1
store.dispatch(Increment())
// Current State: 2
store.dispatch(Decrement())
// Current State: 1

//Removes the reference to the subscription functions.
//Must be called when subscription is no longer needed to avoid a 
//memory leak.
unsubscribe()
```

Instead of mutating the state directly, you specify the mutations you want to happen with plain
objects called _actions_. Then you write a special function called a _reducer_ to decide how every
action transforms the entire application's state.

In a typical Redux app, there is just a single store with a single root reducing function. As your
app grows, you split the root reducer into smaller reducers independently operating on the different
parts of the state tree. Redux is unopinionated - how reducers and actions are organized is up to
you. Useful patterns will be documented here soon.

This architecture might seem like an overkill for a counter app, but the beauty of this pattern is
how well it scales to large and complex apps. It also enables very powerful developer tools, because
it is possible to trace every mutation to the action that caused it. You can record user sessions
and reproduce them just by replaying every action.

## Examples

The repository contains several example projects demonstrating various aspects of how to use
ReduxKotlin:

- [**TaskFlow**](/introduction/examples#taskflow--the-reference-architecture): [Source](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/taskflow) — Compose Multiplatform Kanban app on the bundle stack
- [**Counter**](/introduction/examples#counter): [Source](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/counter)
- [**Todos**](/introduction/examples#todos): [Source](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/todos)

## Help and Discussion

The **[#redux channel](https://kotlinlang.slack.com/messages/C8A8G5F9Q)** of the 
**[KotlinLang Slack](http://kotlinlang.slack.com)** is our channel for ReduxKotlin questions 
and collaborating. Invites are [here.](https://slack.kotlinlang.org) 

## Should You Use Redux?

Redux can be a powerful pattern and has grown in popularity for mobile developers, however it is
still a lesser practiced architecture, so some of the best practices and patterns for your app will
be up to you. Redux looks very promising as a pattern for multiplatform apps targeting iOS, Android,
& possibly web. Especially considering the new declarative UI frameworks Jetpack Compose and
SwiftUI. Many of the samples and multiplatform patterns are experimental at this point. The library
itself is stable, however the best practices for multiplatform apps are still being decided and the
ecosystem is still forming. For anyone interested, this is an opportunity to contribute to libraries
and samples.

This site will continue to grow and post examples, best practices, FAQs, and tutorials.  


Here are some suggestions on when it makes sense to use Redux:

- You have reasonable amounts of data changing over time
- You need a single source of truth for your state
