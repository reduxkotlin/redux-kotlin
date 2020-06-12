---
id: getting-started
title: Getting Started with Redux
sidebar_label: Getting Started with Redux
hide_title: true
---

# Getting Started with ReduxKotlin

ReduxKotlin is a predictable state container for Kotlin apps that supports all platforms that Kotlin
can target (JVM, Native, JS, WASM).

ReduxKotlin is a port of [Redux for Javascript](https://redux.js.org) that aims to become a standard
implementation of Redux for Kotlin. Much of the information on [redux.js.org](https://redux.js.org)
applies to ReduxKotlin, however, there is also a lot of Javascript specific information that may
_not_ apply to ReduxKotlin. This site documents how to get started with ReduxKotlin and provides 
examples of using ReduxKotlin in projects. There are suggestions and examples, but ultimately the 
core ReduxLibrary is not opinionated. How you use it in your project is up to you. This site will 
continue to be updated as Kotlin multiplatform matures.


## Installation

ReduxKotlin is available on Maven central.  

__For a multiplatform project:__

Artifacts are published with Gradle meta-data, so the dependency only needs to be declared in your
common sourceset:

```groovy
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation "org.reduxkotlin:redux-kotlin-threadsafe:0.5.0"
            }
        }
    }
}
```

Gradle meta-data is a preview feature, and will need to be turned on with 
`enableFeaturePreview("GRADLE_METADATA")` in your gradle.settings file.

__For single platform project (i.e. just Android):__

```groovy
dependencies {
    implementation "org.reduxkotlin:redux-kotlin-threadsafe-jvm:0.5.0"
}
```

NOTE: If threadsafety is not a concern (i.e. a JS only project) "org.reduxkotlin:redux-kotlin:0.5.0" may be used.
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
val store = createStore(reducer, 0)

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

The ReduxKotlin Github organization contains several example projects demonstrating various aspects of how to use ReduxKotlin.

- [**Counter**](/introduction/examples#counter): [Source](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/counter)
- [**Todos**](/introduction/examples#todos): [Source](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/todos)
- [**Name Game**](/introduction/examples#namegame): [Source](https://github.com/reduxkotlin/NameGameSampleApp)
- [**Reading List**](/introduction/examples#readinglist): [Source](https://github.com/reduxkotlin/ReadingListSampleApp)
- [**MovieSwiftUI-Kotlin**](/introduction/examples#movieswiftui-kotlin): [Source](https://github.com/reduxkotlin/MovieSwiftUI-kotlin)

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
