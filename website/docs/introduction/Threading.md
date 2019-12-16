---
id: threading
title: Redux on Multi-threaded Platforms
sidebar_label: Threading
hide_title: true
---

# Redux on Multi-threaded Platforms

Redux in multi-threaded environments brings additional concerns that are not present in redux
for Javascript.  Javascript is single threaded, so Redux.js did not have to address the issue.
Android, iOS, and native do have threading, and as such attention must be paid to which threads interact with the store.

As of ReduxKotlin 0.3.0 there is `same thread enforcement` for the [getState](../api/store#getstate-_or_-state-property), [dispatch](../api/store#dispatchaction-any-any), [replaceReducer](../api/store#replacereducernextreducer-reducer-state-unit),
and [subscribe](../api/store#subscribelistener-storesubscriber) functions on the store.  This means these methods must be called from the same thread where
the store was created.  An `IllegalStateException` will be thrown if one of these are called from a 
different thread.

If this `same thread enforcement` was not in place invalid states and race conditions could happen.  
For example if `getState` was called at the same time as a dispatch, the state would represent a past
state.  Or 2 actions dispatched concurrently could cause an invalid state.

Note that this is __SAME__ thread enforcement - not __MAIN__ thread enforcement.  ReduxKotlin does not
force you to use the main thread, although you can if you'd like.  Most mobile applications do redux on the main
thread, however it could be moved to a background thread.  Using a background thread could be desirable 
if the reducers & middleware processing produce UI effects such as dropped frames.


Currently `same thread enforcement` is implemented for JVM, iOS, & macOS.  The other platforms
have do not have the enforcement in place yet.
