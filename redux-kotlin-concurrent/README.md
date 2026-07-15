# redux-kotlin-concurrent

A thread-safe [redux-kotlin](../redux-kotlin) store with **lock-free reads** and
**reentrant-lock-serialized writes** (the *CallerSerialized* strategy). This is
the recommended thread-safe store; it supersedes
[`redux-kotlin-threadsafe`](../redux-kotlin-threadsafe).

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-concurrent:<version>")
```

(Already included transitively by [`redux-kotlin-bundle`](../redux-kotlin-bundle).)

## Quick start

```kotlin
import org.reduxkotlin.concurrent.createConcurrentStore

val store = createConcurrentStore(reducer, AppState())
// reads are lock-free; writes from any thread are serialized:
store.dispatch(Increment)
println(store.state.count)
```

`createConcurrentStore(reducer, preloadedState, notificationContext, onError, enhancer)`
keeps the core contract. `NotificationContext` controls which thread subscribers
are notified on; `coalescingNotificationContext` serializes notifications onto
the target thread when possible. `onError` isolates listener failures.

## See also

- [Threading guide](https://reduxkotlin.org/introduction/threading) · [Concurrent store](https://reduxkotlin.org/advanced/concurrent-store)
- Migrating from threadsafe: [`redux-kotlin-threadsafe`](../redux-kotlin-threadsafe)
