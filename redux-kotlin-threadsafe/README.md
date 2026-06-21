# redux-kotlin-threadsafe

> **Deprecated.** Use [`redux-kotlin-concurrent`](../redux-kotlin-concurrent)
> instead — it offers the same contract with lock-free reads and serialized
> writes. This module remains for existing consumers and will be removed in a
> future release.

An `atomicfu`-locked wrapper around the core [redux-kotlin](../redux-kotlin)
store providing `createThreadSafeStore` / `createTypedThreadSafeStore` and
`Store.asThreadSafe()`.

## Migration

```kotlin
// before
val store = createThreadSafeStore(reducer, state, enhancer = enhancer)
// after
val store = createConcurrentStore(reducer, state, enhancer = enhancer)
```

See the [threading guide](https://reduxkotlin.org/introduction/threading) for
migration notes.
