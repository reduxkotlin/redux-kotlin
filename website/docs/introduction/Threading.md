---
id: threading
title: Redux on Multi-threaded Platforms
sidebar_label: Threading
hide_title: true
---

# Redux on Multi-threaded Platforms

TLDR; use [createThreadSafeStore()](../api/createThreadSafeStore.md), unless your app is Javascript only.

Redux in multi-threaded environments brings additional concerns that are not present in redux
for Javascript.  Javascript is single threaded, so Redux.js did not have to address the issue.
Android, iOS, and native do have threading, and as such attention must be paid to which threads interact with the store.

In a multi-threaded system invalid states and race conditions can happen.
For example if `getState` was called at the same time as a dispatch, the state could represent a past
state.  Or 2 actions dispatched concurrently could cause an invalid state.

**So you there are 3 options:**

1) Synchronize access to the store  - [createThreadSafeStore()](../api/createThreadSafeStore.md)
2) Only access the store from the same thread - [createSameThreadEnforcedStore()](../api/createSameThreadEnforcedStore.md)
3) Live in the wild west and access store anytime, any thread
    and live with consequences - NOT_RECOMMENDED - [createStore()](../api/createStore.md)

ReduxKotlin allows all these, but most cases will fall into #1.

## ThreadSafe

Starting with ReduxKotlin 0.5.0 there is a threadsafe store which uses synchronization (AtomicFu library)
to allow safe access to all the functions on the store.  This is the recommended usage for 90% of use cases.

```kotlin
    val store = createThreadSafeStore(reducer, state)
```

Who is the other 10%? If you are only targeting Javascript thread safety is not an issue, so
`createStore` is the way to go.  Other possible reasons could be applications that need optimal
performance, however it is unlikely that the extra overhead from synchronization will be an issue.

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
                implementation "org.reduxkotlin:redux-kotlin:0.5.5"
            }
        }
    }
}
```
**ONLY USE THE ABOVE IF YOU DO NOT WANT THREAD SAFETY**

