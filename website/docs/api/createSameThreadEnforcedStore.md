---
id: createSameThreadEnforcedstore
title: createSameThreadEnforcedStore
sidebar_label: createSameThreadEnforcedStore
hide_title: true
---

# `createSameThreadEnforcedStore(reducer, preloadedState, enhancer)

Creates a Redux [store](Store.md) that can only be accessed from the same thread.  
Any call to the store's functions called from a thread other than thread from which  
 it was created will throw an IllegalStateException.

 *Strongly* recommended that [`createThreadSafeStore()`](./createThreadSafeStore.md) is used unless there is
 good reason to do otherwise.

 see [`createStore()`](./createStore.md) for usage.
