---
id: createsamethreadenforcedstore
title: createSameThreadEnforcedStore
sidebar_label: createSameThreadEnforcedStore
---

# `createSameThreadEnforcedStore(reducer, preloadedState, enhancer)`

Creates a Redux [store](./store-api) that can only be accessed from the same thread.  
Any call to the store's functions called from a thread other than thread from which  
 it was created will throw an IllegalStateException.

 *Strongly* recommended that [`createThreadSafeStore()`](./createthreadsafestore) is used unless there is
 good reason to do otherwise.

 see [`createStore()`](./createstore) for usage.
