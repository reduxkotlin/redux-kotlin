---
id: createthreadsafestore
title: createThreadSafeStore
sidebar_label: createThreadSafeStore
---

# `createThreadSafeStore(reducer, preloadedState, enhancer)`

:::caution Deprecated

`redux-kotlin-threadsafe` is **deprecated** in favor of `redux-kotlin-concurrent`.
`createConcurrentStore(reducer, preloadedState, enhancer = enhancer)` keeps the same
contract with lock-free reads and serialized writes — for most code it is a drop-in
replacement. See [Threading](../introduction/threading) for migration notes.

:::

Creates a Redux [store](./store-api) that is may be accessed from any thread.

Every store function is synchronized on one lock. If you want reads that never block
(lock-free `getState`/`subscribe` with serialized writes), use
`createConcurrentStore` from `redux-kotlin-concurrent` instead — see
[Threading](../introduction/threading).

There is some performance overhead in using `createThreadSafeStore()` due to
synchronization, however it is small and mostly likely not impact UI.

Some quick benchmarks on an Android app have shown that:

1. calling `getState` was always < 1ms with or without synchronization
2. `dispatch` calls to 1-3ms longer with synchronization
3. During a cold launch of app differences were more dramatic.  Some calls to `dispatch` took +100ms more than an store that was not synchronized.

see [`createStore()`](./createstore) for usage.
