---
id: threading
title: Redux on Multi-threaded Platforms
sidebar_label: Threading
---

# Redux on Multi-threaded Platforms

TLDR; use a **concurrent store** (`createConcurrentStore` from
`redux-kotlin-concurrent`, or the bundle's `createConcurrentModelStore`),
unless your app is Javascript only. The older fully-synchronized
[createThreadSafeStore()](../api/createthreadsafestore) is **deprecated**.

Redux in multi-threaded environments brings additional concerns that are not present in redux
for Javascript.  Javascript is single threaded, so Redux.js did not have to address the issue.
Android, iOS, and native do have threading, and as such attention must be paid to which threads interact with the store.

In a multi-threaded system invalid states and race conditions can happen.
For example if `getState` was called at the same time as a dispatch, the state could represent a past
state.  Or 2 actions dispatched concurrently could cause an invalid state.

**So there are 4 options:**

1) Lock-free reads + serialized writes - `createConcurrentStore` (`redux-kotlin-concurrent`)
2) Synchronize all access to the store  - [createThreadSafeStore()](../api/createthreadsafestore) - DEPRECATED
3) Only access the store from the same thread - [createSameThreadEnforcedStore()](../api/createsamethreadenforcedstore)
4) Live in the wild west and access store anytime, any thread
    and live with consequences - NOT_RECOMMENDED - [createStore()](../api/createstore)

ReduxKotlin allows all of these; most apps should use #1.

## Concurrent store

`redux-kotlin-concurrent` (and the `redux-kotlin-bundle`, which builds on it)
provides the `CallerSerialized` strategy: `getState` and `subscribe` are
**lock-free** — they never block, even during an in-flight dispatch — while
writes (`dispatch`) are serialized through a reentrant writer lock. Reads off
the dispatching thread return an atomic state mirror published at the end of
each dispatch; reads on the dispatching thread see the in-progress state,
matching core Redux. A `NotificationContext` controls which thread subscriber
callbacks run on (UI apps marshal them to the main thread — see
[Compose integration](../advanced/compose-integration#lifecycle-and-threading)).

```kotlin
val store = createConcurrentStore(reducer, initialState)
```

This trades strict read synchronization for non-blocking reads: off-thread
reads are eventually consistent (they may briefly observe the previous state
while a dispatch is completing).
See the [Concurrent Store guide](../advanced/concurrent-store) for
`NotificationContext`, error isolation, and migration notes.

## ThreadSafe (deprecated)

`redux-kotlin-threadsafe` is **deprecated** in favor of
`redux-kotlin-concurrent`. Since ReduxKotlin 0.5.0 it provided a store that
synchronizes (via the AtomicFu library) every function on the store: every read
and write takes the same lock, so reads block during an in-flight dispatch
(which the concurrent store above avoids).

```kotlin
    val store = createThreadSafeStore(reducer, state)
```

For most code `createConcurrentStore(reducer, preloadedState, enhancer = enhancer)`
is a drop-in replacement — see
[createThreadSafeStore](../api/createthreadsafestore) for migration notes.

If you are only targeting Javascript thread safety is not an issue, so
`createStore` is the way to go.

## Same thread enforcement

Another option is `same thread enforcement`. This means these methods must be called from the same thread where
the store was created.  An `IllegalStateException` will be thrown if one of these are called from a 
different thread.


`Same thread enforcement` was the default behavior for ReduxKotlin 0.3.0 - 0.4.0.

Note that this is __SAME__ thread enforcement - not __MAIN__ thread enforcement.  ReduxKotlin does not
force you to use the main thread, although you can if you'd like.  Most mobile applications do redux on the main
thread, however it could be moved to a background thread.  Using a background thread could be desirable 
if the reducers & middleware processing produce UI effects such as dropped frames.

Currently `same thread enforcement` is implemented for JVM, iOS, & macOS.  The other platforms
have do not have the enforcement in place yet.

To use `same thread enforcement`:
```kotlin
    val store = createSameThreadEnforcedStore(reducer, state)
```

## Wild west - no enforcement

`createStore()` may be used if the project is only a single threaded application (i.e. JS only), or you
just want control access within your codebase(NOT_RECOMMENDED).

***IF*** you are using vanilla `createStore()`, then you may use an different artifact,  
and avoid pulling in AtomicFu dependency:

```groovy
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation "org.reduxkotlin:redux-kotlin:0.6.0"
            }
        }
    }
}
```
**ONLY USE THE ABOVE IF YOU DO NOT WANT THREAD SAFETY**

