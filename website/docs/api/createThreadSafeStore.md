---
id: createThreadSafeStore
title: createThreadSafeStore
sidebar_label: createThreadSafeStore
hide_title: true
---

# `createThreadSafeStore(reducer, preloadedState, enhancer)

Creates a Redux [store](Store.md) that is may be accessed from any thread.

***This is the recommended way to create a store.***

There is some performance overhead in using `createThreadSafeStore()` due to
synchronization, however it is small and mostly likely not impact UI.

Some quick benchmarks on an Android app have shown that:

1. calling `getState` was always < 1ms with or without synchronization
2. `dispatch` calls to 1-3ms longer with synchronization
3. During a cold launch of app differences were more dramatic.  Some calls to `dispatch` took +100ms more than an store that was not synchronized.

see [`createStore()`](./createStore.md) for usage.
